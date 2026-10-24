package com.animegraph.graph;

import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="localgraph.integration",matches="true")
class LocalWorkDeletionIntegrationTest {
    @Test void backupAndDeleteAreScopedAndPreserveSharedEntitiesOtherWorksAndLocalExtensions()throws Exception {
        String group=UUID.randomUUID().toString(),work="anime:integration:"+group,other=work+"-other";
        var params=Map.<String,Object>of("group",group,"work",work,"other",other,"person","person:integration:"+group,"character","character:integration:"+group);
        var json=new ObjectMapper().findAndRegisterModules();
        try(var driver=GraphDatabase.driver(System.getenv("NEO4J_URI"),AuthTokens.basic(System.getenv("NEO4J_USERNAME"),System.getenv("NEO4J_PASSWORD")))) {
            var store=new GraphStore(driver,System.getenv("NEO4J_DATABASE"),json);
            try {
                store.write("CREATE (a:Entity:Anime {uid:$work,testGroup:$group}),(b:Entity:Anime {uid:$other,testGroup:$group}),(p:Entity:Person {uid:$person,testGroup:$group}),(c:Entity:Character {uid:$character,testGroup:$group}), (w:LocalGraphWorkspace {uid:$work,testGroup:$group}),(l:LocalGraphEntity {uid:'local:'+ $group,workUid:$work,testGroup:$group}), (w)-[:LOCAL_TEST]->(l), (p)-[:PARTICIPATED_IN]->(a),(c)-[:APPEARS_IN]->(a),(c)-[:APPEARS_IN]->(b),(p)-[:VOICED {animeUid:$work}]->(c),(p)-[:VOICED {animeUid:$other}]->(c)",params);
                var archive=Map.<String,Object>of("payload","current","previous_payload","previous","status","COMPLETE");
                var result=store.deleteLocalWork(work,archive);assertEquals(3,result.get("deletedRelationships"));
                assertTrue(store.read("MATCH (n:Entity:Anime {uid:$work}) RETURN n",params).isEmpty());
                assertEquals(3L,store.read("MATCH (n:Entity {testGroup:$group}) RETURN count(n) AS total",params).getFirst().get("total"));
                assertEquals(2L,store.read("MATCH (:Entity {testGroup:$group})-[r]->(:Entity {testGroup:$group}) RETURN count(r) AS total",params).getFirst().get("total"));
                assertEquals(1L,store.read("MATCH (:LocalGraphWorkspace {uid:$work})-[r]->(:LocalGraphEntity {workUid:$work}) RETURN count(r) AS total",params).getFirst().get("total"));
                var backup=store.read("MATCH (b:LocalWorkDeletionBackup {workUid:$work}) RETURN b.payload AS payload",params);
                var payload=json.readTree((String)backup.getFirst().get("payload"));
                assertEquals(1,payload.path("nodes").size());assertEquals(3,payload.path("relationships").size());assertEquals("previous",payload.path("savedArchive").path("previous_payload").asText());
                assertEquals("NOT_FOUND",assertThrows(ApiException.class,()->store.deleteLocalWork(work,Map.of())).code());
                // Saved archive may exist even if an earlier graph projection never succeeded.
                assertEquals(true,store.deleteLocalWork(work,archive).get("deleted"));
                Files.writeString(Path.of("../.local/local-work-deletion-integration.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("passed",true,"removedRelationships",3,"sharedEntitiesRetained",2,"otherWorkRelationshipsRetained",2,"localExtensionRetained",true,"backupIncludesPreviousArchive",true,"scope","Isolated temporary records, cleaned in finally")));
            }finally {
                store.write("MATCH (n) WHERE n.testGroup=$group OR (n:LocalWorkDeletionBackup AND n.workUid IN [$work,$other]) DETACH DELETE n",params);
            }
        }
    }
}
