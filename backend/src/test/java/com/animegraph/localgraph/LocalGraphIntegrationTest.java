package com.animegraph.localgraph;

import static com.animegraph.localgraph.LocalGraphModels.*;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="localgraph.integration",matches="true")
class LocalGraphIntegrationTest {
    @Test void persistsReopensRestoresAndSerializesConcurrentWritersWithoutTouchingOfficialRecords()throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();String work="anime:integration:"+UUID.randomUUID();
        try(var driver=GraphDatabase.driver(System.getenv("NEO4J_URI"),AuthTokens.basic(System.getenv("NEO4J_USERNAME"),System.getenv("NEO4J_PASSWORD")))) {
            var store=new GraphStore(driver,System.getenv("NEO4J_DATABASE"),json);
            for(String statement:Files.readString(Path.of("src/main/resources/neo4j/V004__local_graph.cypher")).split(";"))if(!statement.isBlank())store.write(statement,Map.of());
            var before=store.read("MATCH (n:Entity) RETURN count(n) AS nodes",Map.of());
            var repository=new LocalGraphRepository(driver,System.getenv("NEO4J_DATABASE"),json);
            String a=LocalGraphRulesTest.nodeId(),org=LocalGraphRulesTest.nodeId();
            var nodes=List.of(LocalGraphRulesTest.node(a,"CHARACTER"),LocalGraphRulesTest.node(org,"ORGANIZATION"));
            var links=List.of(LocalGraphRulesTest.link(a,org,"MEMBER","队长",true),LocalGraphRulesTest.link("character:bangumi:777",a,"OTHER","验收连线",true));
            try {
                var first=repository.replace(new Document(0,1,work,nodes,links));assertEquals(1,first.version());
                assertEquals(2,repository.read(work).nodes().size());assertEquals(2,repository.read(work).edges().size());
                // Re-open through a fresh Driver, rather than relying on any service memory.
                try(var other=GraphDatabase.driver(System.getenv("NEO4J_URI"),AuthTokens.basic(System.getenv("NEO4J_USERNAME"),System.getenv("NEO4J_PASSWORD")))) {
                    var reopened=new LocalGraphRepository(other,System.getenv("NEO4J_DATABASE"),json).read(work);assertEquals(first,reopened);
                }
                var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
                try {
                    var calls=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)calls.add(pool.submit(()->{gate.await();try{repository.replace(first);return true;}catch(ApiException e){assertEquals("LOCAL_GRAPH_CONFLICT",e.code());return false;}}));
                    gate.countDown();int successes=0;for(var call:calls)if(call.get(20,TimeUnit.SECONDS))successes++;assertEquals(1,successes);
                } finally {pool.shutdownNow();}
                var current=repository.read(work);repository.replace(new Document(current.version(),1,work,List.of(),List.of()));
                var backups=repository.backups(work);assertEquals(3,backups.size());
                var backup=backups.stream().map(item->repository.backup(work,(String)item.get("id"))).filter(item->!item.nodes().isEmpty()).findFirst().orElseThrow();
                var restored=repository.replace(new Document(repository.read(work).version(),1,work,backup.nodes(),backup.edges()));assertEquals(2,restored.nodes().size());
                assertEquals(before,store.read("MATCH (n:Entity) RETURN count(n) AS nodes",Map.of()));
                assertTrue(store.read("MATCH (n:LocalGraphEntity {workUid:$work}) WHERE n:Entity RETURN n",Map.of("work",work)).isEmpty());
                var evidence=Map.of("passed",true,"driverReopened",true,"concurrentWriterSuccesses",1,"automaticBackups",3,"restoredEntities",2,"officialEntityCountUnchanged",true,"scope","Isolated temporary local workspace, removed in finally");
                Files.writeString(Path.of("../.local/local-graph-integration.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
            } finally {
                store.write("MATCH (n) WHERE (n:LocalGraphWorkspace AND n.uid=$work) OR ((n:LocalGraphEntity OR n:LocalGraphAnchor OR n:LocalGraphBackup) AND n.workUid=$work) DETACH DELETE n",Map.of("work",work));
            }
        }
    }
}
