package com.animegraph.charactergraph;

import static com.animegraph.charactergraph.CharacterGraphModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

@Repository
public class DemoCharacterGraphRepository implements CharacterGraphRepository {
    private final List<DemoWork> graphs;

    public DemoCharacterGraphRepository(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("fixtures/character-relations.json").getInputStream()) {
            graphs = List.copyOf(mapper.readValue(input, DemoDataset.class).works());
        }
        validate();
    }

    public List<Work> listWorks(int limit) {
        return graphs.stream().map(DemoWork::work).limit(limit).toList();
    }

    public Optional<GraphData> findGraph(String animeUid, int maxNodes, int maxEdges) {
        return graphs.stream().filter(graph -> graph.work().uid().equals(animeUid))
                .map(graph -> new GraphData(graph.work(), List.copyOf(graph.nodes()), List.copyOf(graph.edges()), false))
                .findFirst();
    }

    private void validate() {
        Set<String> works = new HashSet<>();
        for (DemoWork graph : graphs) {
            if (!works.add(graph.work().uid())) throw new IllegalStateException("Duplicate demo work");
            Set<String> nodes = new HashSet<>();
            Set<String> edges = new HashSet<>();
            for (var node : graph.nodes()) {
                if (!nodes.add(node.id()) || !node.id().startsWith("character:fixture:")) {
                    throw new IllegalStateException("Invalid demo character identity");
                }
            }
            for (var edge : graph.edges()) {
                if (!edges.add(edge.id()) || !nodes.contains(edge.source()) || !nodes.contains(edge.target())
                        || edge.source().equals(edge.target()) || edge.directed() != edge.relationType().directed()
                        || edge.evidence().isBlank()) {
                    throw new IllegalStateException("Invalid demo relationship");
                }
            }
            if (nodes.size() != graph.work().characterCount() || edges.size() != graph.work().relationCount()) {
                throw new IllegalStateException("Demo counts do not match data");
            }
        }
    }
}
