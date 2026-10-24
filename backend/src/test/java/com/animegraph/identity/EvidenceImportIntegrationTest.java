package com.animegraph.identity;

import com.animegraph.audit.AuditService;
import com.animegraph.explore.OnlineNetworkService;
import com.animegraph.graph.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit opt-in; unique fixture world, no external-source writes and no real facts changed. */
@EnabledIfSystemProperty(named="evidence.integration",matches="true")
class EvidenceImportIntegrationTest {
    @Test void sharedGlobalEvidenceInvalidatesAllWorldsAndDoesNotResurrect()throws Exception{
        var json=EvidencePackageTest.JSON;String run=UUID.randomUUID().toString(),a="universe:fixture:global-"+run+"-a",b="universe:fixture:global-"+run+"-b",from="character:fixture:global-"+run+"-a",to="character:fixture:global-"+run+"-b";
        var batches=new HashSet<String>();var evidence=new HashSet<String>();var assertions=new HashSet<String>();
        try(var driver=GraphDatabase.driver(System.getenv().getOrDefault("NEO4J_URI","bolt://127.0.0.1:7687"),AuthTokens.basic(System.getenv().getOrDefault("NEO4J_USERNAME","neo4j"),Objects.requireNonNull(System.getenv("NEO4J_PASSWORD"))))){
            String db=System.getenv().getOrDefault("NEO4J_DATABASE","neo4j");var graph=new GraphStore(driver,db,json);var repository=new EvidenceImportRepository(driver,db,json);var first=new EvidenceImportService(repository,json,mock(AuditService.class));var second=new EvidenceImportService(repository,json,mock(AuditService.class));
            try{
                graph.write("CREATE CONSTRAINT evidence_import_batch_uid IF NOT EXISTS FOR (n:EvidenceImportBatch) REQUIRE n.uid IS UNIQUE",Map.of());
                graph.write("MATCH (anime:Entity:Anime {uid:'anime:bangumi:328609'}) CREATE (s:Entity:Character:MaintenanceFixture {uid:$from,name:'Synthetic A',active:true,runId:$run}),(t:Entity:Character:MaintenanceFixture {uid:$to,name:'Synthetic B',active:true,runId:$run}) WITH anime,s,t UNWIND $worlds AS uid CREATE (w:Universe:MaintenanceFixture {uid:uid,activeGeneration:'fixture-g1',runId:$run}) CREATE (anime)-[:IN_UNIVERSE]->(w),(s)-[:WORLD_APPEARANCE {worldUid:uid,generation:'fixture-g1',subjectId:328609,role:'测试'}]->(anime),(t)-[:WORLD_APPEARANCE {worldUid:uid,generation:'fixture-g1',subjectId:328609,role:'测试'}]->(anime)",Map.of("from",from,"to",to,"worlds",List.of(a,b),"run",run));
                var universe=spy(new UniverseService(graph,mock(OnlineNetworkService.class),mock(ExternalIdentityMapper.class),json));
                for(String uid:List.of(a,b))doReturn(new UniverseService.World(uid,"Synthetic only","Fixture","series:fixture:global",List.of(new UniverseService.Subject(328609,"fixture",1,"https://example.org")))).when(universe).world(uid);
                var root=EvidencePackageTest.input();root.put("worldUid",a).put("batchId","global-"+run+"-a");EvidencePackageTest.row(root).put("source",from).put("target",to);var other=root.deepCopy();other.put("worldUid",b).put("batchId","global-"+run+"-b");var p=EvidencePackage.parse(root,json);var q=EvidencePackage.parse(other,json);track(p,batches,evidence,assertions);track(q,batches,evidence,assertions);assertEquals(p.facts().getFirst().evidenceUid(),q.facts().getFirst().evidenceUid());
                var stale=second.preview(json.writeValueAsBytes(other),8);first.apply(first.preview(json.writeValueAsBytes(root),7).previewId(),7);assertThrows(com.animegraph.common.ApiException.class,()->second.apply(stale.previewId(),8));
                String before=universe.network(a,List.of(),true,"","",false).version();second.apply(second.preview(json.writeValueAsBytes(other),8).previewId(),8);assertNotEquals(before,universe.network(a,List.of(),true,"","",false).version());
                first.disable(p.batchId(),"fixture shared source",7);assertEquals(1,universe.network(a,List.of(),true,"","",false).edges().size());assertEquals(1,universe.network(b,List.of(),true,"","",false).edges().size());
                String aVersion=universe.network(a,List.of(),true,"","",false).version();String bVersion=universe.network(b,List.of(),true,"","",false).version();second.disable(q.batchId(),"fixture final source",8);
                assertTrue(universe.network(a,List.of(),true,"","",false).edges().isEmpty());assertTrue(universe.network(b,List.of(),true,"","",false).edges().isEmpty());assertNotEquals(aVersion,universe.network(a,List.of(),true,"","",false).version());assertNotEquals(bVersion,universe.network(b,List.of(),true,"","",false).version());
                other.put("batchId","global-"+run+"-replay");assertFalse(second.preview(json.writeValueAsBytes(other),8).canApply());
            }finally{
                graph.write("UNWIND $ids AS uid MATCH (e:Evidence {uid:uid}) DETACH DELETE e",Map.of("ids",List.copyOf(evidence)));
                graph.write("UNWIND $ids AS uid MATCH (a:RelationAssertion {uid:uid}) DETACH DELETE a",Map.of("ids",List.copyOf(assertions)));
                graph.write("UNWIND $ids AS uid MATCH (b:EvidenceImportBatch {uid:uid}) DETACH DELETE b",Map.of("ids",List.copyOf(batches)));
                graph.write("MATCH (n:MaintenanceFixture {runId:$run}) DETACH DELETE n",Map.of("run",run));
                assertTrue(graph.read("MATCH (n:MaintenanceFixture {runId:$run}) RETURN n.uid AS uid LIMIT 1",Map.of("run",run)).isEmpty());
            }
        }
    }
    @Test void atomicReviewReplaySharedEvidenceScopeRevocationAndRefresh()throws Exception{
        var json=EvidencePackageTest.JSON;String run=UUID.randomUUID().toString();String world="universe:fixture:review-"+run,from="character:fixture:review-"+run+"-a",to="character:fixture:review-"+run+"-b";
        var evidenceIds=new HashSet<String>();var assertionIds=new HashSet<String>();var batchIds=new HashSet<String>();
        try(var driver=GraphDatabase.driver(System.getenv().getOrDefault("NEO4J_URI","bolt://127.0.0.1:7687"),AuthTokens.basic(System.getenv().getOrDefault("NEO4J_USERNAME","neo4j"),Objects.requireNonNull(System.getenv("NEO4J_PASSWORD"),"Private database environment required")))){
            String db=System.getenv().getOrDefault("NEO4J_DATABASE","neo4j");var graph=new GraphStore(driver,db,json);var repository=new EvidenceImportRepository(driver,db,json);var service=new EvidenceImportService(repository,json,mock(AuditService.class));
            try{
                graph.write("CREATE CONSTRAINT evidence_import_batch_uid IF NOT EXISTS FOR (n:EvidenceImportBatch) REQUIRE n.uid IS UNIQUE",Map.of());
                // Existing real anime are read only. All new nodes/relations belong to this unique fixture world.
                graph.write("MATCH (a:Entity:Anime {uid:'anime:bangumi:328609'}),(b:Entity:Anime {uid:'anime:bangumi:55770'}) CREATE (w:Universe:MaintenanceFixture {uid:$world,activeGeneration:'fixture-g1',runId:$run}), (s:Entity:Character:MaintenanceFixture {uid:$from,name:'Synthetic A',nameCn:'测试A',active:true,source:'fixture',runId:$run}), (t:Entity:Character:MaintenanceFixture {uid:$to,name:'Synthetic B',nameCn:'测试B',active:true,source:'fixture',runId:$run}) CREATE (a)-[:IN_UNIVERSE]->(w),(b)-[:IN_UNIVERSE]->(w),(s)-[:WORLD_APPEARANCE {worldUid:$world,generation:'fixture-g1',subjectId:328609,role:'测试'}]->(a),(t)-[:WORLD_APPEARANCE {worldUid:$world,generation:'fixture-g1',subjectId:328609,role:'测试'}]->(a),(s)-[:WORLD_APPEARANCE {worldUid:$world,generation:'fixture-g1',subjectId:55770,role:'测试'}]->(b),(t)-[:WORLD_APPEARANCE {worldUid:$world,generation:'fixture-g1',subjectId:55770,role:'测试'}]->(b)",Map.of("world",world,"from",from,"to",to,"run",run));
                assertEquals(1L,graph.read("MATCH (w:Universe {uid:$uid}) RETURN count(w) AS count",Map.of("uid",world)).getFirst().get("count"));
                var universe=spy(new UniverseService(graph,mock(OnlineNetworkService.class),mock(ExternalIdentityMapper.class),json));
                doReturn(new UniverseService.World(world,"Synthetic only","Fixture","series:fixture:review",List.of(new UniverseService.Subject(328609,"fixture season A",1,"https://example.org"),new UniverseService.Subject(55770,"fixture season B",2,"https://example.org")))).when(universe).world(world);
                var root=EvidencePackageTest.input();root.put("worldUid",world).put("batchId","review-"+run+"-1");EvidencePackageTest.row(root).put("source",from).put("target",to).put("scopeType","SUBJECT_EXPLICIT").putArray("subjectIds").add(328609);
                var p=EvidencePackage.parse(root,json);track(p,batchIds,evidenceIds,assertionIds);
                var preview=service.preview(json.writeValueAsBytes(root),7);assertTrue(preview.canApply(),preview.blockers().toString());assertTrue(universe.network(world,List.of(),true,"","",false).edges().isEmpty());
                service.apply(preview.previewId(),7);service.apply(preview.previewId(),7);
                var network=universe.network(world,List.of(328609L),true,"","",true);assertEquals(1,network.edges().size());String version=network.version();assertEquals(1,((List<?>)network.edges().getFirst().get("evidences")).size());assertTrue(universe.network(world,List.of(55770L),true,"","",false).edges().isEmpty());
                // Same evidence in another reviewed batch is shared, not duplicated.
                var shared=root.deepCopy();shared.put("batchId","review-"+run+"-2");var sharedPackage=EvidencePackage.parse(shared,json);track(sharedPackage,batchIds,evidenceIds,assertionIds);service.apply(service.preview(json.writeValueAsBytes(shared),7).previewId(),7);
                service.disable(p.batchId(),"synthetic first batch disabled",7);assertEquals(1,universe.network(world,List.of(),true,"","",true).edges().size());
                service.disable(sharedPackage.batchId(),"synthetic last batch disabled",7);assertTrue(universe.network(world,List.of(),true,"","",true).edges().isEmpty());assertFalse(universe.network(world,List.of(),true,"","",true).version().equals(version));assertFalse(service.preview(json.writeValueAsBytes(root),7).canApply());
                // A different batch must not revive the same withdrawn evidence.
                var replay=root.deepCopy();replay.put("batchId","review-"+run+"-replay");assertFalse(service.preview(json.writeValueAsBytes(replay),7).canApply());
                // A separately reviewed conflicting relation remains a separate assertion.
                var conflict=root.deepCopy();conflict.put("batchId","review-"+run+"-3");EvidencePackageTest.row(conflict).put("relationType","RIVAL").put("sourceRevision","r2");var cp=EvidencePackage.parse(conflict,json);track(cp,batchIds,evidenceIds,assertionIds);var review=service.preview(json.writeValueAsBytes(conflict),7);
                graph.write("MATCH (w:Universe {uid:$world}) SET w.evidenceRevision=w.evidenceRevision+1",Map.of("world",world));assertThrows(com.animegraph.common.ApiException.class,()->service.apply(review.previewId(),7));assertTrue(repository.list(100).stream().noneMatch(b->b.get("uid").equals(cp.batchUid())));
                service.apply(service.preview(json.writeValueAsBytes(conflict),7).previewId(),7);assertEquals(1,universe.network(world,List.of(),true,"","",true).edges().size());
                // Refresh the fixture generation and verify reviewed provenance is retained.
                graph.write("MATCH (:MaintenanceFixture {uid:$from})-[r:WORLD_APPEARANCE]->(:Anime) SET r.generation='fixture-g2' WITH count(r) AS ignored MATCH (:MaintenanceFixture {uid:$to})-[t:WORLD_APPEARANCE]->(:Anime) SET t.generation='fixture-g2' WITH count(t) AS ignored MATCH (w:Universe {uid:$world}) SET w.activeGeneration='fixture-g2'",Map.of("world",world,"from",from,"to",to));assertEquals(1,universe.network(world,List.of(),true,"","",true).edges().size());
                service.revoke(cp.facts().getFirst().uid(),"synthetic reviewed assertion withdrawn",7);assertTrue(universe.network(world,List.of(),true,"","",true).edges().isEmpty());assertFalse(service.preview(json.writeValueAsBytes(conflict),7).canApply());
                // Source refresh semantics (ON CREATE active=true) cannot reopen the tombstone.
                graph.write("MERGE (a:RelationAssertion {uid:$uid}) ON CREATE SET a.active=true",Map.of("uid",cp.facts().getFirst().uid()));assertTrue(universe.network(world,List.of(),true,"","",true).edges().isEmpty());

            }finally{
                // Delete only exact IDs derived from this run's unique synthetic endpoints.
                graph.write("UNWIND $ids AS uid MATCH (e:Evidence {uid:uid}) DETACH DELETE e",Map.of("ids",List.copyOf(evidenceIds)));
                graph.write("UNWIND $ids AS uid MATCH (a:RelationAssertion {uid:uid}) DETACH DELETE a",Map.of("ids",List.copyOf(assertionIds)));
                graph.write("UNWIND $ids AS uid MATCH (b:EvidenceImportBatch {uid:uid}) DETACH DELETE b",Map.of("ids",List.copyOf(batchIds)));
                graph.write("MATCH (n:MaintenanceFixture {runId:$run}) DETACH DELETE n",Map.of("run",run));
                assertTrue(graph.read("MATCH (n:MaintenanceFixture {runId:$run}) RETURN n.uid AS uid LIMIT 1",Map.of("run",run)).isEmpty());
            }
        }
    }
    private static void track(EvidencePackage p,Set<String> batches,Set<String> evidence,Set<String> assertions){batches.add(p.batchUid());p.facts().forEach(f->{evidence.add(f.evidenceUid());assertions.add(f.uid());});}
}
