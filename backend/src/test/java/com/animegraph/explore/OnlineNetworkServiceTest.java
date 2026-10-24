package com.animegraph.explore;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.animegraph.common.ApiException;
import com.animegraph.viewer.BangumiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OnlineNetworkServiceTest {
    final BangumiClient client=mock(BangumiClient.class);
    final BangumiAnimeService anime=mock(BangumiAnimeService.class);
    final ObjectMapper json=new ObjectMapper();
    @Test void preferredNameAndSourceAliasesAgreeBetweenRosterAndRelatedNodes() throws Exception {
        String character="{\"id\":125172,\"name\":\"結束バンド\",\"nameCN\":\"纽带乐队\",\"role\":4}";
        when(client.publicNetworkRequest("/p1/subjects/328609/characters?limit=60&offset=0")).thenReturn(json.readTree("{\"total\":1,\"data\":[{\"type\":1,\"character\":"+character+"}]}"));
        when(client.publicNetworkRequest("/p1/characters/87968/relations?limit=100&offset=0")).thenReturn(json.readTree("{\"total\":1,\"data\":[{\"character\":"+character+",\"relation\":{\"id\":2007,\"cn\":\"成员\"}}]}"));
        var service=new OnlineNetworkService(client,anime);
        var roster=service.network(328609,"CHARACTERS",1).nodes().getFirst();
        var related=service.relations(87968).nodes().getFirst();
        assertThat(roster.id()).isEqualTo("character:bangumi:125172");
        assertThat(roster.label()).isEqualTo("结束乐队").isEqualTo(related.label());
        assertThat(roster.aliases()).contains("纽带乐队","結束バンド").isEqualTo(related.aliases());
        assertThat(roster.characterType()).isEqualTo(4);
        assertThat(roster.sourceUrl()).isEqualTo("https://bgm.tv/character/125172");
        assertThat(roster.nameAssertions()).anyMatch(a -> a.kind().equals("PROJECT_PREFERRED"));
    }
    @Test void arbitraryOnlineWorkHasOnlyItsCharactersAndNoSyntheticWorkHub() throws Exception {
        when(client.publicNetworkRequest("/p1/subjects/328609/characters?limit=60&offset=0")).thenReturn(json.readTree("""
          {"total":2,"data":[{"type":1,"character":{"id":87968,"name":"後藤ひとり","nameCN":"后藤一里","role":1,"images":{"medium":"https://evil.test/x"}},"casts":[{"person":{"id":5}}]},{"type":2,"character":{"id":87973,"name":"伊地知虹夏","nameCN":"伊地知虹夏","role":1}}]}
          """));
        var graph=new OnlineNetworkService(client,anime).network(328609,"CHARACTERS",1);
        verify(anime).detail(328609);
        assertThat(graph.nodes()).hasSize(2).allMatch(n -> n.category().equals("CHARACTER"));
        assertThat(graph.nodes().getFirst().label()).isEqualTo("后藤一里");
        assertThat(graph.nodes().getFirst().imageUrl()).isEmpty();
        assertThat(graph.edges()).isEmpty();
        assertThatThrownBy(() -> new OnlineNetworkService(client,anime).network(328609,"ALL",1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new OnlineNetworkService(client,anime).network(328609,"STAFF",5)).isInstanceOf(ApiException.class);
    }
    @Test void reciprocalTeacherRecordsBecomeSameEdgeAndCrushRetainsUnilateralDirection() throws Exception {
        when(client.publicNetworkRequest("/p1/characters/1/relations?limit=100&offset=0")).thenReturn(json.readTree("""
          {"total":2,"data":[{"character":{"id":2,"name":"学生"},"relation":{"id":2011,"cn":"学生"},"spoiler":true,"ended":true},{"character":{"id":3,"name":"喜欢的人"},"relation":{"id":2004,"cn":"单恋"}}]}
          """));
        when(client.publicNetworkRequest("/p1/characters/2/relations?limit=100&offset=0")).thenReturn(json.readTree("""
          {"total":1,"data":[{"character":{"id":1,"name":"老师"},"relation":{"id":2012,"cn":"老师"}}]}
          """));
        var service=new OnlineNetworkService(client,anime);
        var forward=service.relations(1).edges();var reciprocal=service.relations(2).edges().getFirst();
        assertThat(forward.getFirst().id()).isEqualTo(reciprocal.id());
        assertThat(forward.getFirst()).satisfies(e -> {assertThat(e.source()).isEqualTo("character:bangumi:1");assertThat(e.target()).isEqualTo("character:bangumi:2");assertThat(e.label()).isEqualTo("老师");assertThat(e.spoiler()).isTrue();assertThat(e.ended()).isTrue();});
        assertThat(forward.get(1).source()).isEqualTo("character:bangumi:1");assertThat(forward.get(1).target()).isEqualTo("character:bangumi:3");
    }
    @Test void staffGraphSeparatesPeopleAndJobsAndKeepsActualJobLabels() throws Exception {
        when(client.publicNetworkRequest("/p1/subjects/328609/staffs/persons?limit=60&offset=60")).thenReturn(json.readTree("""
          {"total":284,"data":[{"staff":{"id":1,"name":"导演"},"positions":[{"type":{"id":2,"cn":"监督"},"appearEps":"1-12"},{"type":{"id":4,"cn":"分镜"}}]}]}
          """));
        var graph=new OnlineNetworkService(client,anime).network(328609,"STAFF",2);
        assertThat(graph.nodes()).extracting(OnlineNetworkService.Node::category).containsExactly("PERSON","POSITION","POSITION");
        assertThat(graph.edges()).extracting(OnlineNetworkService.Edge::label).containsExactly("监督","分镜");
        assertThat(graph.edges().getFirst().description()).contains("1-12");
        assertThat(graph.meta().truncated()).isTrue();
    }
    @Test void memberAssociationDoesNotTurnAnOrganizationIntoAHumansMember() throws Exception {
        when(client.publicNetworkRequest("/p1/characters/1/relations?limit=100&offset=0")).thenReturn(json.readTree("{\"total\":1,\"data\":[{\"character\":{\"id\":2,\"name\":\"组织\",\"role\":4},\"relation\":{\"id\":2007,\"cn\":\"成员\"}}]}"));
        when(client.publicNetworkRequest("/p1/characters/2/relations?limit=100&offset=0")).thenReturn(json.readTree("{\"total\":1,\"data\":[{\"character\":{\"id\":1,\"name\":\"成员\",\"role\":1},\"relation\":{\"id\":2007,\"cn\":\"成员\"}}]}"));
        var service=new OnlineNetworkService(client,anime);var first=service.relations(1).edges().getFirst();var reverse=service.relations(2).edges().getFirst();
        assertThat(first.directed()).isFalse();assertThat(first.id()).isEqualTo(reverse.id());
    }
    @Test void onlineCacheFallsBackWithExplicitStaleStatusButNeverMasksDeletion() throws Exception {
        var time=new Clock(){Instant now=Instant.EPOCH;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}};
        String path="/p1/characters/1/relations?limit=100&offset=0";
        when(client.publicNetworkRequest(path)).thenReturn(json.readTree("{\"total\":0,\"data\":[]}"));
        var service=new OnlineNetworkService(client,anime,time);
        assertThat(service.relations(1).meta().cached()).isFalse();assertThat(service.relations(1).meta().cached()).isTrue();
        verify(client,times(1)).publicNetworkRequest(path);
        time.now=Instant.EPOCH.plusSeconds(301);
        when(client.publicNetworkRequest(path)).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,"UPSTREAM","offline"));
        assertThat(service.relations(1).meta().stale()).isTrue();
        doThrow(new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","deleted")).when(client).publicNetworkRequest(path);
        assertThatThrownBy(() -> service.relations(1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.relations(-1)).isInstanceOf(ApiException.class);
    }
}
