package com.animegraph.charactergraph;

import static com.animegraph.charactergraph.CharacterGraphModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

class CharacterGraphServiceTest {
    private CharacterGraphService service;
    private Neo4jCharacterGraphRepository neo4j;

    @BeforeEach
    void setup() throws IOException {
        neo4j = mock(Neo4jCharacterGraphRepository.class);
        service = new CharacterGraphService(new DemoCharacterGraphRepository(new ObjectMapper()), neo4j);
    }

    @Test
    void demoCatalogAndGraphAreExplicitlyLabelled() {
        var catalog = service.works(DataSource.DEMO);
        assertThat(catalog.items()).hasSize(2);
        assertThat(catalog.meta().demo()).isTrue();
        var graph = service.graph(DataSource.DEMO, "anime:fixture:starlit", 100);
        assertThat(graph.nodes()).hasSize(8);
        assertThat(graph.edges()).hasSize(13);
        assertThat(graph.meta().notice()).contains("人工构造");
        verifyNoInteractions(neo4j);
    }

    @Test
    void parallelRelationshipsAndMentorDirectionArePreserved() {
        var graph = service.graph(DataSource.DEMO, "anime:fixture:starlit", 100);
        var shared = graph.edges().stream().filter(edge ->
                edge.source().endsWith("-linxing") && edge.target().endsWith("-sucheng")).toList();
        assertThat(shared).extracting(CharacterEdge::relationType)
                .containsExactlyInAnyOrder(RelationType.FRIEND, RelationType.TEAMMATE);
        assertThat(graph.edges().stream().filter(edge -> edge.relationType() == RelationType.MENTOR))
                .allSatisfy(edge -> assertThat(edge.directed()).isTrue());
        assertThat(graph.edges().stream().filter(edge -> edge.source().endsWith("-jichuan")
                && edge.target().endsWith("-linxing"))).hasSize(1);
    }

    @Test
    void nodeLimitsNeverReturnOrphanEdges() {
        var graph = service.graph(DataSource.DEMO, "anime:fixture:starlit", 3);
        var ids = graph.nodes().stream().map(CharacterNode::id).collect(Collectors.toSet());
        assertThat(graph.nodes()).hasSize(3);
        assertThat(graph.meta().truncated()).isTrue();
        assertThat(graph.edges()).allSatisfy(edge -> {
            assertThat(ids).contains(edge.source(), edge.target());
        });
        assertThat(service.graph(DataSource.DEMO, "anime:fixture:mist", 100).nodes())
                .noneMatch(node -> node.id().contains("starlit"));
    }

    @Test
    void unknownWorksAndInvalidLimitsAreRejected() {
        assertThatThrownBy(() -> service.graph(DataSource.DEMO, "anime:fixture:missing", 100))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.graph(DataSource.DEMO, "anime:fixture:starlit", 201))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status().value()).isEqualTo(400));
    }

    @Test
    void databaseFailuresNeverSilentlySubstituteDemoData() {
        when(neo4j.listWorks(51)).thenThrow(new ServiceUnavailableException("private connection detail"));
        assertThatThrownBy(() -> service.works(DataSource.NEO4J))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(503);
                    assertThat(error.code()).isEqualTo("GRAPH_UNAVAILABLE");
                    assertThat(error.getMessage()).doesNotContain("private connection detail");
                });
    }
}
