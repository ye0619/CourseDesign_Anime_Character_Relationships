package com.animegraph.explore;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.animegraph.catalog.CatalogService;
import com.animegraph.common.*;
import com.animegraph.graph.GraphStore;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExploreServiceTest {
    @Test void allCompleteLocalWorksUseLocalFirst() {
        var curatedLocal=Map.<String,Object>of("uid","anime:bangumi:328609","source","bangumi","sourceId","328609","name","本地名称","summary","简介");
        when(catalog.detail("ANIME","anime:bangumi:328609")).thenReturn(curatedLocal);
        assertThat(service.detail(328609).dataSource()).isEqualTo("LOCAL");
        assertThat(service.detail(328609,"LOCAL").entity()).containsEntry("libraryStatus","ARCHIVED");
        verifyNoInteractions(online);
        when(online.detail(328609)).thenReturn(new BangumiAnimeService.Snapshot<>(curatedLocal,Instant.EPOCH,false,false));
        assertThat(service.detail(328609,"ONLINE").dataSource()).isEqualTo("ONLINE");
        verify(online).detail(328609);
    }
    @Test void missingOrFailedLocalDoesNotSilentlyFetchOnlineAndOnlineFailureDoesNotMixSources() {
        when(catalog.detail("ANIME","anime:bangumi:328609")).thenThrow(new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","missing"));
        when(online.detail(328609)).thenReturn(new BangumiAnimeService.Snapshot<>(Map.of("uid","anime:bangumi:328609"),Instant.EPOCH,false,false));
        assertThat(service.detail(328609).dataSource()).isEqualTo("ONLINE");
        clearInvocations(online);
        doReturn(local).when(catalog).detail("ANIME","anime:bangumi:328609");
        when(online.detail(328609)).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,"UPSTREAM","offline"));
        assertThatThrownBy(()->service.detail(328609,"ONLINE")).isInstanceOf(ApiException.class);
    }
    final CatalogService catalog=mock(CatalogService.class);
    final GraphStore graph=mock(GraphStore.class);
    final BangumiAnimeService online=mock(BangumiAnimeService.class);
    final ExploreService service=new ExploreService(catalog,graph,online);
    final Map<String,Object> local=Map.of("uid","anime:bangumi:12","nameCn","人工保护名称","source","bangumi","sourceId","12");
    @Test void joinsByStableIdWithoutOverwritingLocalNamesOrCombiningTotals() {
        when(catalog.list("ANIME","星",null,"",true,1,6,null)).thenReturn(new PageResult<>(List.of(local),1,6,1));
        var entity=Map.<String,Object>of("uid","anime:bangumi:12","nameCn","在线中文名","sourceId","12");
        when(online.search("星",null,"",2,12)).thenReturn(new BangumiAnimeService.Snapshot<>(new PageResult<>(List.of(entity),2,12,100),Instant.EPOCH,false,false));
        when(graph.read(anyString(),anyMap())).thenReturn(List.of(Map.of("entity",local)));
        var result=service.search("星",null,"","MIXED",2,1);
        assertThat(result.online().total()).isEqualTo(100);assertThat(result.local().total()).isEqualTo(1);
        assertThat(result.online().items().getFirst()).containsEntry("localAvailable",true).containsEntry("nameCn","在线中文名");
        assertThat(result.local().items().getFirst()).containsEntry("nameCn","人工保护名称");
        verify(graph,never()).write(anyString(),anyMap());
    }
    @Test void onlineFailureKeepsLocalAndLocalOnlyMakesNoRemoteRequests() {
        when(catalog.list(anyString(),anyString(),isNull(),anyString(),eq(true),eq(1),eq(6),isNull())).thenReturn(new PageResult<>(List.of(local),1,6,1));
        when(online.search(anyString(),isNull(),anyString(),anyInt(),anyInt())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,"BANGUMI_UNAVAILABLE","offline"));
        var result=service.search("",null,"","MIXED",1,1);
        assertThat(result.local().items()).hasSize(1);assertThat(result.meta().onlineStatus()).isEqualTo("ERROR");
        clearInvocations(online);assertThat(service.search("",null,"","LOCAL",1,1).meta().onlineStatus()).isEqualTo("SKIPPED");verifyNoInteractions(online);
    }
    @Test void onlineOnlyDetailWorksWithoutLocalAndRejectsInvalidFilters() {
        when(catalog.detail("ANIME","anime:bangumi:12")).thenThrow(new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","missing"));
        when(online.detail(12)).thenReturn(new BangumiAnimeService.Snapshot<>(Map.of("uid","anime:bangumi:12"),Instant.EPOCH,false,false));
        var result=service.detail(12);assertThat(result.localEntity()).isNull();assertThat(result.entity()).containsEntry("localAvailable",false).containsEntry("localChecked",true);
        assertThatThrownBy(() -> service.search("",1700,"","MIXED",1,1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.search("",null,"bad","MIXED",1,1)).isInstanceOf(ApiException.class);
    }
}
