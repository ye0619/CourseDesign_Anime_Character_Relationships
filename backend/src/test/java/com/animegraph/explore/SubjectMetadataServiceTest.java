package com.animegraph.explore;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.animegraph.common.ApiException;
import com.animegraph.identity.NameResolutionService;
import com.animegraph.viewer.BangumiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SubjectMetadataServiceTest {
    final ObjectMapper json = new ObjectMapper();
    final BangumiClient client = mock(BangumiClient.class);
    final BangumiAnimeService anime = mock(BangumiAnimeService.class);
    final NetworkSourceCache cache = new NetworkSourceCache(client);
    final NameResolutionService names = new NameResolutionService(json);
    final SubjectMetadataService service = new SubjectMetadataService(cache, anime, names);
    void source(String kind, int page, String body) throws Exception {
        when(client.publicNetworkRequest("/p1/subjects/1/" + (kind.equals("CAST") ? "characters" : "staffs/persons") + "?limit=60&offset=" + ((page-1)*60))).thenReturn(json.readTree(body));
    }
    @Test void preservesMultipleRolesLanguagesVersionsAndIdOnlyPeopleWithoutGuessingOriginalLanguage() throws Exception {
        source("CAST",1,"""
          {"total":2,"data":[{"character":{"id":10,"name":"甲"},"casts":[
            {"person":{"id":5,"type":1},"relation":0},{"person":{"id":5,"type":1},"relation":3},
            {"person":{"id":5,"type":1},"relation":4,"version":"重制","appearEps":"1-3"},
            {"person":{"id":6,"name":"同名","type":3},"relation":2},
            {"person":{"id":7,"name":"同名"},"relation":99}]},
            {"character":{"id":11,"name":"乙"},"casts":[{"person":{"id":5,"type":1},"relation":0}]}]}
          """);
        var page=service.page(1,"CAST",1);
        assertThat(page.people()).hasSize(3); assertThat(page.cast()).hasSize(6);
        assertThat(page.cast().getFirst().language()).isNull();
        assertThat(page.cast().get(1).language()).isEqualTo("zh");
        assertThat(page.cast().get(1).languageSource()).isEqualTo("relation");
        assertThat(page.cast().get(2)).satisfies(c -> {assertThat(c.language()).isEqualTo("ja");assertThat(c.version()).isEqualTo("重制");assertThat(c.episodeScope()).isEqualTo("1-3");});
        assertThat(page.cast().get(3).performanceType()).isEqualTo("LIVE_ACTION");
        assertThat(page.cast().get(4).performanceType()).isEqualTo("UNKNOWN");
        assertThat(page.coverage().completeness()).isEqualTo("COMPLETE");
        assertThat(service.page(1,"CAST",1).snapshotVersion()).isEqualTo(page.snapshotVersion());
        assertThat(service.page(1,"CAST",1).source().cached()).isTrue();
        verify(client,times(1)).publicNetworkRequest(anyString());
    }
    @Test void missingCastsInvalidRowsAndEmptyCastsHaveDifferentCoverage() throws Exception {
        source("CAST",1,"""
          {"total":4,"data":[{"character":{"id":10}}, {"character":{"id":11},"casts":[]},
          {"character":{"id":12},"casts":[{"person":{}}]}]}
          """);
        var page=service.page(1,"CAST",1);
        assertThat(page.coverage().unknownCastRows()).isEqualTo(1);
        assertThat(page.coverage().omittedRows()).isEqualTo(1);
        assertThat(page.coverage().omittedCredits()).isEqualTo(1);
        assertThat(page.coverage().completeness()).isEqualTo("PARTIAL");
    }
    @Test void staffMergesJobsKeepsOrganizationsAndUsesSourceCursorBeyondCanvasLimit() throws Exception {
        source("STAFF",5,"""
          {"total":241,"data":[{"staff":{"id":3,"type":2},"positions":[{"type":{"id":2,"cn":"监督"},"appearEps":"1"},{"type":{"id":4,"cn":"分镜"}}]}]}
          """);
        var page=service.page(1,"STAFF",5);
        assertThat(page.people().getFirst().providerType()).isEqualTo(2);
        assertThat(page.staff().getFirst().positions()).hasSize(2);
        assertThat(page.coverage().exhausted()).isTrue();assertThat(page.coverage().nextPage()).isNull();
        assertThat(page.coverage().completeness()).isEqualTo("PARTIAL");
        source("STAFF",100,"{\"total\":6001,\"data\":[{\"staff\":{\"id\":3},\"positions\":[]}]}");
        assertThat(service.page(1,"STAFF",100).coverage().capped()).isTrue();
        assertThatThrownBy(() -> service.page(1,"STAFF",101)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.page(1,"ALL",1)).isInstanceOf(ApiException.class);
    }
    @Test void graphAndCreditsShareRawPageAndStaffFailureDoesNotRemoveCast() throws Exception {
        source("CAST",1,"{\"total\":1,\"data\":[{\"character\":{\"id\":10},\"casts\":[]}]}");
        new OnlineNetworkService(cache,anime,names).network(1,"CHARACTERS",1);
        service.page(1,"CAST",1);
        verify(client,times(1)).publicNetworkRequest(anyString());
        when(client.publicNetworkRequest(contains("staffs"))).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,"UPSTREAM","offline"));
        assertThatThrownBy(() -> service.page(1,"STAFF",1)).isInstanceOf(ApiException.class);
        assertThat(service.page(1,"CAST",1).characters()).hasSize(1);
    }
    @Test void permissionRevocationEvictsPreviouslyPublicCacheAndNeverFallsBack() throws Exception {
        var time=new Clock(){Instant now=Instant.EPOCH;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}};
        var sources=new NetworkSourceCache(client,time);
        String path="/p1/subjects/1/characters?limit=60&offset=0";
        when(client.publicNetworkRequest(path)).thenReturn(json.readTree("{\"total\":0,\"data\":[]}"));
        sources.fetch(path);time.now=time.now.plusSeconds(301);
        when(client.publicNetworkRequest(path)).thenThrow(new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","private"));
        assertThatThrownBy(() -> sources.fetch(path)).isInstanceOf(ApiException.class);
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY,"UPSTREAM","offline")).when(client).publicNetworkRequest(path);
        assertThatThrownBy(() -> sources.fetch(path)).isInstanceOf(ApiException.class);
    }
    @Test void contradictoryTotalsNeverClaimComplete() throws Exception {
        source("CAST",1,"{\"total\":0,\"data\":[{\"character\":{\"id\":10},\"casts\":[]}]}");
        assertThat(service.page(1,"CAST",1).coverage().completeness()).isEqualTo("PARTIAL");
    }
}
