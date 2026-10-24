package com.animegraph.viewer;

import com.animegraph.explore.*;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewingReportV2Test {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private ReportCredits.Participation person(String uid,String role){return new ReportCredits.Participation(uid,"同名",1,role);}
    private ReportCredits.Work local(long id,List<ReportCredits.Participation> people){
        var section=new ReportCredits.Section("NOT_VERIFIED","NOT_VERIFIED",null,people.size());
        return new ReportCredits.Work(id,"NEO4J_LOCAL","local-version",section,section,people);
    }
    @Test void overallDeduplicatesAcrossRolesAndCastWhileCountsAllWorks(){
        var works=List.of(RecommendationEngineTest.work(1424,2,8,List.of()),RecommendationEngineTest.work(3774,2,0,List.of()),
                RecommendationEngineTest.work(2,2,6,List.of()),RecommendationEngineTest.work(3,3,10,List.of()));
        var first=local(1424,List.of(person("p1","导演"),person("p1","编剧"),person("p1","配音"),person("p1","配音"),person("p2","导演")));
        var second=local(3774,List.of(person("p1","导演")));
        var third=local(2,List.of(person("p1","编剧")));
        var report=ViewingReportAnalyzer.analyze(works,Map.of(1424L,first,3774L,second,2L,third));
        assertEquals(3,report.completed());
        var overall=report.overall().stream().filter(p->p.uid().equals("p1")).findFirst().orElseThrow();
        assertEquals(3,overall.workCount());assertEquals(2,overall.ratedCount());assertEquals(7.0,overall.averageRating());
        assertNull(overall.adjustedRating());assertEquals(List.of("导演","编剧","配音"),overall.roles());
        assertEquals(2,report.overall().size()); // Same display name never merges different identities.
        assertEquals(0,report.metadataCovered());
    }
    private CreditModels.Page page(String kind,Instant at,boolean stale){
        var person=new CreditModels.Entity("person:bangumi:1","同名","","","PERSON",1,List.of(),List.of(),"v","url");
        return new CreditModels.Page("anime:bangumi:2",kind,List.of(person),List.of(),
                kind.equals("STAFF")?List.of(new CreditModels.StaffCredit(person.id(),List.of(new CreditModels.Position("1","导演",null,null)))):List.of(),
                List.of(),new CreditModels.Coverage(1,60,1,1,1,0,0,0,null,true,false,"COMPLETE"),
                new CreditModels.Source("BANGUMI","url","field","adapter",at,false,stale,"names"),"page-version");
    }
    @Test void collectedFactsRemainCompleteAfterExpiryAndDoNotAcquireAnotherSubjectsCredits(){
        var old=Instant.now().minusSeconds(7*3600);
        var staff=page("STAFF",old,true);var cast=page("CAST",old,true);
        var input=ReportCredits.fromSnapshot(new SubjectSnapshotService.Snapshot(2,
                new SubjectSnapshotService.Section("CAST",List.of(cast),1,1,false,null,null),
                new SubjectSnapshotService.Section("STAFF",List.of(staff),1,1,false,null,null),null));
        assertEquals("COMPLETE",input.staff().collectionStatus());assertEquals("STALE",input.staff().freshness());
        var report=ViewingReportAnalyzer.analyze(List.of(RecommendationEngineTest.work(2,2,8,List.of())),Map.of(2L,input));
        assertEquals(1,report.metadataCovered());assertEquals(1,report.coverageSummary().staff().freshness().get("STALE"));
        var wrong=ReportCredits.fromSnapshot(new SubjectSnapshotService.Snapshot(3,
                new SubjectSnapshotService.Section("CAST",List.of(cast),1,1,false,null,null),
                new SubjectSnapshotService.Section("STAFF",List.of(staff),1,1,false,null,null),null));
        assertTrue(wrong.participation().isEmpty());assertEquals("PARTIAL",wrong.staff().collectionStatus());
    }
    @Test void localReaderUsesOnlyWatchedIdsAndVersionsChangedCredits(){
        var graph=mock(GraphStore.class);var reader=new LocalReportCredits(graph,json);
        var row=new HashMap<String,Object>(Map.of("uid","anime:bangumi:1424","updatedAt","a","staff",
                List.of(Map.of("uid","p1","name","同名","role","导演","recordUid","r1")),"cast",List.of()));
        when(graph.read(anyString(),anyMap())).thenReturn(List.of(row));
        var first=reader.read(List.of(1424L,2L,1424L));
        verify(graph).read(anyString(),eq(Map.of("uids",List.of("anime:bangumi:1424","anime:bangumi:2"))));
        assertEquals("NOT_VERIFIED",first.get(1424L).staff().collectionStatus());
        row.put("staff",List.of(Map.of("uid","p1","name","同名","role","编剧","recordUid","r1")));
        assertNotEquals(first.get(1424L).version(),reader.read(List.of(1424L)).get(1424L).version());
        reset(graph);when(graph.read(anyString(),anyMap())).thenReturn(List.of());assertTrue(reader.read(List.of(2L)).isEmpty());verify(graph).read(anyString(),eq(Map.of("uids",List.of("anime:bangumi:2"))));
    }
    private ViewerModels.Binding binding(String revision){
        return new ViewerModels.Binding("private",1,"user","用户",null,"PUBLIC",revision,"IDLE",0,0,false,null,Instant.EPOCH);
    }
    @Test void reportBuildReusesLocalCreditsWithoutFetchingWhenSupplementDisabled() throws Exception {
        var viewers=mock(ViewerService.class);var mapper=mock(ViewerAnalysisMapper.class);
        var snapshots=mock(SubjectSnapshotService.class);var local=mock(LocalReportCredits.class);
        var binding=binding("revision");var state=new ViewerService.ViewerData(binding,List.of(RecommendationEngineTest.work(1424,2,8,List.of())));
        when(viewers.snapshot("private")).thenReturn(state);when(viewers.binding("private")).thenReturn(binding);
        when(snapshots.readMany(anyList())).thenReturn(Map.of());when(local.read(anyList())).thenReturn(Map.of(1424L,local(1424,List.of(person("p1","导演")))));
        var saved=new CountDownLatch(1);var payload=new AtomicReference<String>();
        when(mapper.save(anyString(),anyString(),any(),anyString())).thenAnswer(call->{payload.set(call.getArgument(3));saved.countDown();return 1;});
        var service=new ViewingReportService(viewers,mapper,snapshots,json,local);
        try{
            service.start("private",false);assertTrue(saved.await(5,TimeUnit.SECONDS));
            var report=json.readValue(payload.get(),ViewingReportService.Report.class);
            assertEquals(1,report.overall().size());assertEquals("NEO4J_LOCAL",report.coverage().getFirst().source());
            verify(snapshots,never()).start(anyLong(),anyBoolean());verify(snapshots,atLeastOnce()).readMany(List.of(1424L));
        }finally{service.close();}
    }
    @Test void cancelOrAccountChangeWhileLocalReadIsPendingCannotPublishOldReport() throws Exception {
        for(boolean cancel:List.of(true,false)){
            var viewers=mock(ViewerService.class);var mapper=mock(ViewerAnalysisMapper.class);
            var snapshots=mock(SubjectSnapshotService.class);var local=mock(LocalReportCredits.class);
            var current=new AtomicReference<>(binding("revision"));
            when(viewers.snapshot("private")).thenAnswer(call->new ViewerService.ViewerData(current.get(),List.of(RecommendationEngineTest.work(1424,2,8,List.of()))));
            when(viewers.binding("private")).thenAnswer(call->current.get());when(snapshots.readMany(anyList())).thenReturn(Map.of());
            var reading=new CountDownLatch(1);var proceed=new CountDownLatch(1);var readDone=new CountDownLatch(1);
            when(local.read(anyList())).thenAnswer(call->{reading.countDown();proceed.await(3,TimeUnit.SECONDS);readDone.countDown();return Map.of();});
            var service=new ViewingReportService(viewers,mapper,snapshots,json,local);
            try{
                service.start("private",false);assertTrue(reading.await(5,TimeUnit.SECONDS));
                if(cancel)service.cancel("private");else current.set(binding("other-revision"));
                proceed.countDown();assertTrue(readDone.await(5,TimeUnit.SECONDS));service.close();
                verify(mapper,never()).save(anyString(),anyString(),any(),anyString());
                if(cancel)assertEquals("CANCELLED",service.read("private").task().status());
                else assertNull(service.read("private").task());
            }finally{proceed.countDown();service.close();}
        }
    }
    @Test void previousSavedReportsRemainReadableWithoutInventingNewCoverage() throws Exception {
        var legacy=json.readValue("""
            {"version":"viewing-report/1","generatedAt":"2026-10-22T00:00:00Z","completed":1,"rated":0,
             "metadataConsidered":0,"metadataCovered":0,"personnel":[],"methodology":["历史口径"]}
            """,ViewingReportService.Report.class);
        assertEquals(1,legacy.completed());assertNull(legacy.coverageSummary());
    }
}
