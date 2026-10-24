package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.AuthTokens;

/** Opt-in read-only benchmark over the actual active projection, never synthetic facts in the source database. */
@EnabledIfSystemProperty(named="connections.integration",matches="true")
class ConnectionIntegrationTest {
    @Test void actualProjectionAndThreeScopedSizes()throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();Projection full;
        try(var driver=GraphDatabase.driver(System.getenv("NEO4J_URI"),AuthTokens.basic(System.getenv("NEO4J_USERNAME"),System.getenv("NEO4J_PASSWORD")))) {
            var repository=new ConnectionProjectionRepository(driver,System.getenv("NEO4J_DATABASE"),json);full=repository.active();
        }
        assertNotNull(full,"Build an actual public projection before the explicit integration benchmark");
        assertTrue(full.nodes().stream().allMatch(n->Set.of("CHARACTER","VOICE_ACTOR").contains(n.kind())));
        var runs=new ArrayList<Object>();var random=new Random(10242);var endpoints=new ArrayList<Endpoint[]>();
        for(int works:List.of(5,20,full.subjects().size())) {
            var subjects=full.subjects().stream().limit(works).toList();var ids=new HashSet<>(subjects.stream().map(Subject::subjectId).toList());
            var facts=full.facts().stream().filter(f->ids.contains(f.subjectId())).toList();var uids=new HashSet<String>();facts.forEach(f->{uids.add(f.source());uids.add(f.target());});
            var p=new Projection(full.revision(),full.builtAt(),full.nodes().stream().filter(n->uids.contains(n.uid())).toList(),facts,full.appearances().stream().filter(e->ids.contains(e.subjectId())&&uids.contains(e.characterUid())).toList(),subjects,full.adapterVersion());
            var timings=new ArrayList<Long>();var queries=new ArrayList<Object>();
            for(int i=0;i<12;i++) {
                var a=p.appearances().get(random.nextInt(p.appearances().size()));var b=p.appearances().get(random.nextInt(p.appearances().size()));if(a.subjectId()==b.subjectId()||a.characterUid().equals(b.characterUid())){i--;continue;}
                if(works==full.subjects().size())endpoints.add(new Endpoint[]{a,b});
                var query=new Query(a,b,8,1,false,"CONNECTIVITY",null,false,null,false,false,p.revision(),true);
                long start=System.nanoTime();var result=ConnectionSearchEngine.search("benchmark",p,query,ConnectionSearchEngine.Budget.defaults(),()->false,r->{});long elapsed=(System.nanoTime()-start)/1_000_000;timings.add(elapsed);
                assertEquals(p.revision(),result.graphRevision());if(!result.searchExhausted())assertNull(result.exactPathCount());
                for(var path:result.paths())assertEquals(path.nodes().size(),path.nodes().stream().map(PathNode::uid).distinct().count());
                queries.add(Map.of("from",a,"to",b,"elapsedMillis",elapsed,"states",result.states(),"edgeChecks",result.edgeChecks(),"stopReason",result.stopReason(),"paths",result.paths().stream().map(ConnectionModels.Path::hopCount).toList(),"shortestProven",result.shortestProven()));
            }
            Collections.sort(timings);var degree=new HashMap<String,Integer>();facts.forEach(f->{degree.merge(f.source(),1,Integer::sum);degree.merge(f.target(),1,Integer::sum);});
            runs.add(Map.of("works",subjects.size(),"nodes",p.nodes().size(),"facts",facts.size(),"maxDegree",degree.values().stream().mapToInt(Integer::intValue).max().orElse(0),"p95Millis",timings.get((int)Math.ceil(timings.size()*.95)-1),"queries",queries));
        }
        var sensitivity=new ArrayList<Object>(); // Reuse exactly the full-scope random endpoints above, without selecting by reachability.
        for(String condition:List.of("VOICE_ONLY","VOICE_AND_STORY","WITH_SPOILERS","MAX_4_HOPS")) {
            var p=condition.equals("VOICE_ONLY")?new Projection(full.revision(),full.builtAt(),full.nodes(),full.facts().stream().filter(f->f.kind().equals("VOICE")).toList(),full.appearances(),full.subjects(),full.adapterVersion()):full;
            int reached=0,interrupted=0;var observations=new ArrayList<Object>();
            for(var pair:endpoints){var q=new Query(pair[0],pair[1],condition.equals("MAX_4_HOPS")?4:8,1,condition.equals("WITH_SPOILERS"),"CONNECTIVITY",null,false,null,false,false,p.revision(),true);var r=ConnectionSearchEngine.search("sensitivity",p,q,ConnectionSearchEngine.Budget.defaults(),()->false,x->{});if(!r.paths().isEmpty())reached++;if(!Set.of("RESULT_LIMIT","EXHAUSTED").contains(r.stopReason()))interrupted++;observations.add(Map.of("from",pair[0],"to",pair[1],"found",!r.paths().isEmpty(),"stopReason",r.stopReason(),"hops",r.paths().stream().map(ConnectionModels.Path::hopCount).toList(),"elapsedMillis",r.elapsedMillis(),"states",r.states(),"edgeChecks",r.edgeChecks()));}
            sensitivity.add(Map.of("condition",condition,"endpointPairs",endpoints.size(),"foundPairs",reached,"interruptedPairs",interrupted,"queries",observations));
        }
        String output=System.getProperty("connections.acceptance.path");if(output!=null)Files.writeString(java.nio.file.Path.of(output),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checkedAt",Instant.now(),"revision",full.revision(),"scope","Read-only actual projections; per-call adjacency construction included; no claim of 10000-node benchmark or independent story fact audit", "runs",runs,"sensitivity",sensitivity)));
    }
}
