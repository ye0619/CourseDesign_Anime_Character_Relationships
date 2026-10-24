package com.animegraph.config;

import com.animegraph.graph.GraphStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class GraphSchemaInitializer implements ApplicationRunner {
    private final GraphStore graph;
    public GraphSchemaInitializer(GraphStore graph) {this.graph=graph;}
    public void run(ApplicationArguments arguments) throws Exception {
        for(String version:java.util.List.of("V001__graph_schema","V002__universe_schema","V003__evidence_import_batches","V004__local_graph","V005__connections"))apply(version);
    }
    private void apply(String file) throws Exception {
        String version=file.substring(0,4);
        byte[] bytes;
        try(var stream=new ClassPathResource("neo4j/"+file+".cypher").getInputStream()) {bytes=stream.readAllBytes();}
        String hash=GraphStore.hash(bytes);
        var applied=graph.read("MATCH (m:SchemaMigration {version:$version}) RETURN m.checksum AS checksum",Map.of("version",version));
        if(!applied.isEmpty()) {
            if(!hash.equals(applied.getFirst().get("checksum"))) throw new IllegalStateException("Applied Neo4j migration checksum changed; add a new migration instead.");
            return;
        }
        for(String statement:new String(bytes,StandardCharsets.UTF_8).split(";")) if(!statement.isBlank()) graph.write(statement,Map.of());
        graph.write("MERGE (m:SchemaMigration {version:$version}) SET m.checksum=$hash,m.appliedAt=$now",Map.of("version",version,"hash",hash,"now",Instant.now().toString()));
    }
}
