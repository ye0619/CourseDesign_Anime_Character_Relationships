package com.animegraph.explore;

import com.animegraph.catalog.CatalogService;
import com.animegraph.charactergraph.CharacterGraphService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SavedAnimeServiceTest {
    final SavedAnimeMapper mapper=mock(SavedAnimeMapper.class);
    final BangumiAnimeService anime=mock(BangumiAnimeService.class);
    final SubjectMetadataService metadata=mock(SubjectMetadataService.class);
    final CharacterRelationProvider relations=mock(CharacterRelationProvider.class);
    final GraphStore graph=mock(GraphStore.class);
    final CatalogService catalog=mock(CatalogService.class);
    final MetadataMapper publicMetadata=mock(MetadataMapper.class);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final AtomicReference<SavedAnimeMapper.Row> row=new AtomicReference<>();
    SavedAnimeService service(){return new SavedAnimeService(mapper,anime,metadata,relations,mock(CharacterGraphService.class),graph,catalog,json,publicMetadata);}
    void memoryMapper(CountDownLatch finished) {
        when(mapper.read(12)).thenAnswer(i->row.get());
        doAnswer(i->{var old=row.get();row.set(new SavedAnimeMapper.Row(12,old==null?null:old.payload(),"PENDING",null,null));return null;}).when(mapper).request(12);
        doAnswer(i->{row.set(new SavedAnimeMapper.Row(12,i.getArgument(1),i.getArgument(2),i.getArgument(3),Instant.now()));return null;}).when(mapper).save(eq(12L),anyString(),anyString(),nullable(String.class));
        doAnswer(i->{var old=row.get();String status=i.getArgument(1);row.set(new SavedAnimeMapper.Row(12,old==null?null:old.payload(),status,i.getArgument(2),Instant.now()));if(!Set.of("PENDING","RUNNING").contains(status))finished.countDown();return null;}).when(mapper).status(eq(12L),anyString(),nullable(String.class));
    }
    CreditModels.Page empty(String kind) {
        return new CreditModels.Page("anime:bangumi:12",kind,List.of(),List.of(),List.of(),List.of(),new CreditModels.Coverage(1,60,0,0,0,0,0,0,null,true,false,"COMPLETE"),new CreditModels.Source("BANGUMI","url","data","v",Instant.now(),false,false,"v"),"v");
    }
    @Test void savesAnySubjectDeduplicatesConcurrentRequestsAndPersistsBeforeGraph()throws Exception {
        var done=new CountDownLatch(1);memoryMapper(done);var fetching=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        when(anime.detail(12)).thenAnswer(i->{fetching.countDown();proceed.await(3,TimeUnit.SECONDS);return new BangumiAnimeService.Snapshot<>(Map.of("name","真实作品","summary","简介"),Instant.now(),false,false);});
        when(catalog.detail(anyString(),anyString())).thenThrow(new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","missing"));
        when(metadata.page(12,"CAST",1)).thenReturn(empty("CAST"));when(metadata.page(12,"STAFF",1)).thenReturn(empty("STAFF"));
        when(relations.relations(eq(12L),anySet())).thenReturn(new CharacterRelationProvider.Result("bangumi","EMPTY","revision","",List.of()));
        var service=service();try {
            service.start(12,false);assertTrue(fetching.await(2,TimeUnit.SECONDS));service.start(12,true);proceed.countDown();assertTrue(done.await(3,TimeUnit.SECONDS));
            assertEquals("COMPLETE",service.status(12).status());assertTrue(service.status(12).saved());verify(anime,times(1)).detail(12);
            var order=inOrder(mapper,graph);order.verify(mapper).save(eq(12L),anyString(),eq("RUNNING"),isNull());order.verify(graph).upsertEntity(eq("bangumi"),any(),anyString());
            verify(publicMetadata).save(eq(12L),eq("CAST"),anyString());assertEquals(0,service.credits(12,"CAST",1).coverage().total());
        }finally{proceed.countDown();service.close();}
    }
    @Test void offlineUpdateRetainsSavedPayloadAndReportsPartial()throws Exception {
        var done=new CountDownLatch(1);memoryMapper(done);
        String original=json.writeValueAsString(new SavedAnimeService.Payload(Map.of("name","原资料"),List.of(empty("CAST")),List.of(empty("STAFF")),"EMPTY","revision"));
        row.set(new SavedAnimeMapper.Row(12,original,"COMPLETE",null,Instant.EPOCH));
        when(anime.detail(12)).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,"OFFLINE","offline"));
        var service=service();try{service.start(12,true);assertTrue(done.await(3,TimeUnit.SECONDS));assertEquals("PARTIAL",service.status(12).status());assertEquals(original,row.get().payload());verifyNoInteractions(graph);assertEquals("原资料",service.read(12).entity().get("name"));}finally{service.close();}
    }
    @Test void deletionBacksUpCompleteArchiveBeforeRemovingSqlAndNeverFetchesOnline() {
        var archive=Map.<String,Object>of("status","COMPLETE","payload","current","previous_payload","previous");
        when(mapper.deletionSnapshot(12)).thenReturn(archive);
        when(graph.deleteLocalWork("anime:bangumi:12",archive)).thenReturn(Map.of("deleted",true));
        var service=service();try {
            assertEquals(true,service.deleteLocal("anime:bangumi:12").get("deleted"));
            var order=inOrder(graph,mapper);order.verify(graph).deleteLocalWork("anime:bangumi:12",archive);order.verify(mapper).delete(12);
            verifyNoInteractions(anime,metadata,publicMetadata);
        }finally{service.close();}
    }
    @Test void deletionRejectsSavingImportsAndMalformedIdsWithoutChangingData() {
        var service=service();try {
            for(String status:List.of("PENDING","RUNNING")) {
                when(mapper.deletionSnapshot(12)).thenReturn(Map.of("status",status));
                assertEquals("SAVE_BUSY",assertThrows(ApiException.class,()->service.deleteLocal("anime:bangumi:12")).code());
            }
            when(mapper.activeImports("fixture")).thenReturn(1);
            assertEquals("IMPORT_BUSY",assertThrows(ApiException.class,()->service.deleteLocal("anime:fixture:a1")).code());
            for(String uid:List.of("person:fixture:p1","anime:bangumi:01","anime:bangumi:2147483648","anime:bangumi:abc","anime:bad/uid"))assertThrows(ApiException.class,()->service.deleteLocal(uid));
            verifyNoInteractions(graph);verify(mapper,never()).delete(anyLong());
        }finally{service.close();}
    }
    @Test void backupFailureKeepsSqlArchiveAndFixtureDeletionDoesNotTouchSql() {
        when(mapper.deletionSnapshot(12)).thenReturn(Map.of("status","COMPLETE"));
        when(graph.deleteLocalWork(eq("anime:bangumi:12"),anyMap())).thenThrow(new IllegalStateException("backup failed"));
        var service=service();try {
            assertThrows(IllegalStateException.class,()->service.deleteLocal("anime:bangumi:12"));verify(mapper,never()).delete(anyLong());
            service.deleteLocal("anime:fixture:a1");verify(graph).deleteLocalWork("anime:fixture:a1",Map.of());
            verify(mapper,never()).delete(anyLong());
        }finally{service.close();}
    }
    @Test void validatesIdsAndRecoversInterruptedJobsWithoutInventingSuccess() {
        var service=service();try{assertThrows(ApiException.class,()->service.start(0,false));service.run(null);verify(mapper).recover();assertEquals("NOT_SAVED",service.status(12).status());}finally{service.close();}
    }
}
