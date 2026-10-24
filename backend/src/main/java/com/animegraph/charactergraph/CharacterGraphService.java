package com.animegraph.charactergraph;

import static com.animegraph.charactergraph.CharacterGraphModels.*;
import com.animegraph.common.ApiException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CharacterGraphService {
    private static final int MAX_EDGES = 400;
    private final CharacterGraphRepository demo;
    private final CharacterGraphRepository neo4j;

    public CharacterGraphService(DemoCharacterGraphRepository demo, Neo4jCharacterGraphRepository neo4j) {
        this.demo = demo;
        this.neo4j = neo4j;
    }

    public Catalog works(DataSource source) {
        return read(() -> {
            var works = repository(source).listWorks(51);
            return new Catalog(works.stream().limit(50).toList(), meta(source, works.size() > 50, 0));
        });
    }

    public GraphResponse graph(DataSource source, String animeUid, int maxNodes) {
        if (maxNodes < 1 || maxNodes > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "角色数量上限必须在1到200之间");
        }
        return read(() -> {
            GraphData graph = repository(source).findGraph(animeUid, maxNodes, MAX_EDGES)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "未找到该作品的角色图谱"));
            var nodes = graph.nodes().stream().limit(maxNodes).toList();
            var ids = nodes.stream().map(CharacterNode::id).collect(Collectors.toSet());
            Set<String> seenEdges = new HashSet<>();
            var eligibleEdges = graph.edges().stream().filter(edge -> ids.contains(edge.source())
                    && ids.contains(edge.target()) && seenEdges.add(edge.id())).toList();
            var edges = eligibleEdges.stream().limit(MAX_EDGES).toList();
            boolean truncated = graph.truncated() || graph.nodes().size() > nodes.size()
                    || eligibleEdges.size() > MAX_EDGES;
            var types = Arrays.stream(RelationType.values())
                    .map(RelationType::definition).toList();
            return new GraphResponse(graph.work(), nodes, edges, types, meta(source, truncated, maxNodes));
        });
    }

    private CharacterGraphRepository repository(DataSource source) {
        if(source==DataSource.ONLINE)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","请使用作品的统一角色图谱接口");
        return source == DataSource.DEMO ? demo : neo4j;
    }

    private Meta meta(DataSource source, boolean truncated, int maxNodes) {
        boolean demo = source == DataSource.DEMO;
        return new Meta(source, demo, truncated, demo
                ? "作品、角色及剧情关系均为人工构造，仅用于功能演示。"
                : "关系按出处展示；Bangumi社区关联与人工维护分别标明来源，未核实的剧情不自动推断。",
                maxNodes, MAX_EDGES);
    }

    private <T> T read(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (Neo4jException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GRAPH_UNAVAILABLE", "项目图谱暂时无法连接，请重试或选择演示数据");
        }
    }
}
