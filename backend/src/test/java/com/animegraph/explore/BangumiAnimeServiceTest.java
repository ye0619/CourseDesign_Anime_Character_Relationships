package com.animegraph.explore;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.animegraph.common.ApiException;
import com.animegraph.viewer.BangumiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BangumiAnimeServiceTest {
    @Test void rankedPoolFiltersUnrankedAndNormalizesSlimSearchScores() throws Exception {
        var client=mock(BangumiClient.class);var json=new ObjectMapper();var response=json.readTree("{\"total\":300,\"data\":[{\"id\":326,\"type\":2,\"name\":\"test\",\"score\":9.2,\"rank\":1,\"short_summary\":\"summary\"}]}");
        when(client.publicAnimeRequest(anyString(),anyMap())).thenReturn(response);
        var row=new BangumiAnimeService(client).rankedCandidates(2).data().items().getFirst();assertThat(row).containsEntry("score",9.2).containsEntry("rank",1).containsEntry("summary","summary");
        verify(client).publicAnimeRequest(eq("/v0/search/subjects?limit=20&offset=20"),argThat(body->((java.util.Map<?,?>)body.get("filter")).get("rank").equals(java.util.List.of(">0","<=300"))));
    }
    @Test void keepsOnlyAnimeDeduplicatesIdsAndUsesFreshThenStaleCache() throws Exception {
        var client=mock(BangumiClient.class);var clock=mock(Clock.class);when(clock.instant()).thenReturn(Instant.EPOCH);
        var service=new BangumiAnimeService(client,clock);
        var body=new ObjectMapper().readTree(""" 
          {"total":2,"data":[{"id":12,"type":2,"name":"Original","name_cn":"中文","images":{"common":"https://evil.test/p.png"}},{"id":12,"type":2,"name":"Original","name_cn":"中文"},{"id":99,"type":1,"name":"Book"}]}
          """);
        when(client.publicAnimeRequest(anyString(),anyMap())).thenReturn(body);
        var first=service.search("中文",2023,"TV",1,12);assertThat(first.data().items()).hasSize(1);assertThat(first.data().items().getFirst()).containsEntry("nameCn","中文").containsEntry("imageUrl","");
        assertThat(service.search("中文",2023,"TV",1,12).cached()).isTrue();verify(client,times(1)).publicAnimeRequest(anyString(),anyMap());
        when(clock.instant()).thenReturn(Instant.EPOCH.plusSeconds(61));
        when(client.publicAnimeRequest(anyString(),anyMap())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,"OFFLINE","offline"));
        assertThat(service.search("中文",2023,"TV",1,12).stale()).isTrue();
        when(clock.instant()).thenReturn(Instant.EPOCH.plusSeconds(601));assertThatThrownBy(() -> service.search("中文",2023,"TV",1,12)).isInstanceOf(ApiException.class);
    }
}
