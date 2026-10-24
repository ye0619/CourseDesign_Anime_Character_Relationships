package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ConnectionSearchEngineTest {
    Node c(String id){return new Node(id,"CHARACTER",id,"",List.of(id));}
    Node v(String id){return new Node(id,"VOICE_ACTOR",id,"",List.of(id));}
    Fact voice(String id,String person,String character,long work){return fact(id,"VOICE",person,character,work,false,false,false,false,null,null);}
    Fact story(String id,String a,String b,long work){return fact(id,"STORY",a,b,work,false,false,false,false,null,null);}
    Fact fact(String id,String kind,String a,String b,long work,boolean directed,boolean spoiler,boolean ended,boolean manual,String language,String version){return new Fact(id,kind,a,b,work,kind,directed,spoiler,ended,manual,language,version,null,"WORK",List.of(new Evidence("SOURCE",id,"https://example.org",id,"v",null,"SOURCE","")));}
    Projection graph(List<Node> nodes,List<Fact> facts){var appearances=new LinkedHashSet<Endpoint>();for(var f:facts){if(f.kind().equals("VOICE"))appearances.add(new Endpoint(f.target(),f.subjectId()));else{appearances.add(new Endpoint(f.source(),f.subjectId()));appearances.add(new Endpoint(f.target(),f.subjectId()));}}return new Projection("revision",Instant.EPOCH,nodes,facts,List.copyOf(appearances),List.of(),"test");}
    Query query(String from,long a,String to,long b){return new Query(new Endpoint(from,a),new Endpoint(to,b),8,50,false,"CONNECTIVITY",null,false,null,false,false,null,true);}
    Result run(Projection graph,Query q){return ConnectionSearchEngine.search("q",graph,q,ConnectionSearchEngine.Budget.defaults(),()->false,r->{});}
    @Test void differentWorksSharingVoiceAreTwoHopsWithTwoEvidenceRecords() {
        var p=graph(List.of(c("a"),c("z"),v("p")),List.of(voice("1","p","a",1),voice("2","p","z",2)));
        var r=run(p,query("a",1,"z",2));assertEquals(1,r.returnedPaths());var path=r.paths().getFirst();assertEquals(2,path.hopCount());assertEquals(1,path.crossWorkCount());assertEquals(2,path.edges().stream().mapToInt(e->e.fact().evidence().size()).sum());assertTrue(r.shortestProven());assertTrue(r.searchExhausted());assertEquals(1L,r.exactPathCount());
    }
    @Test void sixHopChainPreservesWorksAndLogicalIds() {
        var p=graph(List.of(c("a"),c("c"),c("d"),c("f"),c("z"),v("b"),v("e")),List.of(voice("1","b","a",1),voice("2","b","c",3),story("3","c","d",3),voice("4","e","d",3),voice("5","e","f",9),story("6","f","z",9)));
        var r=run(p,query("a",1,"z",9));var path=r.paths().getFirst();assertEquals(6,path.hopCount());assertEquals(4,path.voiceEdgeCount());assertEquals(2,path.storyEdgeCount());assertEquals(2,path.crossWorkCount());assertEquals(List.of(1L,3L,3L,9L,9L),path.nodes().stream().filter(n->n.kind().equals("CHARACTER")).map(PathNode::subjectId).toList());
    }
    @Test void sameWorkVoiceCannotBridgeAndSharedIdentityCannotSwitchSeasons() {
        var p=graph(List.of(c("a"),c("b"),c("z"),v("p")),List.of(voice("1","p","a",1),voice("2","p","b",1),story("3","b","z",2)));
        assertTrue(run(p,query("a",1,"z",2)).paths().isEmpty());
        var seasonal=graph(List.of(c("a"),c("b"),c("z")),List.of(story("1","a","b",1),story("2","b","z",2)));
        assertTrue(run(seasonal,query("a",1,"z",2)).paths().isEmpty());
    }
    @Test void sourceDirectionSpoilersEndedAndManualConditionsAreAppliedBeforeExpansion() {
        var edge=fact("s","STORY","b","z",2,true,true,true,true,null,null);
        var p=graph(List.of(c("a"),c("b"),c("z"),v("p")),List.of(voice("1","p","a",1),voice("2","p","z",2),edge));
        var q=new Query(new Endpoint("a",1),new Endpoint("b",2),8,10,true,"CONNECTIVITY",null,false,null,true,false,null,true);
        var result=run(p,q);assertEquals(3,result.paths().getFirst().hopCount());assertTrue(result.paths().getFirst().edges().get(2).reversed());
        assertTrue(run(p,new Query(q.from(),q.to(),8,10,true,"STRICT",null,false,null,true,false,null,true)).paths().isEmpty());
        assertTrue(run(p,query("a",1,"b",2)).paths().isEmpty());
        assertTrue(run(p,new Query(q.from(),q.to(),8,10,true,"CONNECTIVITY",null,false,null,true,false,null,false)).paths().isEmpty());
    }
    @Test void languageUnknownIsExplicitAndUnknownVersionDoesNotMatchKnownVersion() {
        var p=graph(List.of(c("a"),c("b"),c("z"),v("p")),List.of(voice("1","p","a",1),fact("2","VOICE","p","b",2,true,false,false,false,"ja","TV"),story("3","b","z",2)));
        var q=query("a",1,"b",2);assertEquals(1,run(p,q).returnedPaths());assertEquals(0,run(p,new Query(q.from(),q.to(),8,10,false,"CONNECTIVITY","ja",false,null,false,false,null,true)).returnedPaths());
        assertEquals(1,run(p,new Query(q.from(),q.to(),8,10,false,"CONNECTIVITY","ja",true,null,false,false,null,true)).returnedPaths());assertEquals(0,run(p,query("a",1,"z",2)).returnedPaths());
    }
    @Test void storyRequirementContinuesPastOrdinaryShortestAndKeepsDifferentHistories() {
        var p=graph(List.of(c("a"),c("x"),c("y"),c("z"),v("p"),v("q")),List.of(voice("1","p","a",1),voice("2","p","z",2),story("3","a","x",1),voice("4","q","x",1),voice("5","q","y",2),story("6","y","z",2)));
        var q=query("a",1,"z",2);assertEquals(2,run(p,q).paths().getFirst().hopCount());
        var withStory=run(p,new Query(q.from(),q.to(),8,50,false,"CONNECTIVITY",null,false,null,false,true,null,true));assertEquals(4,withStory.paths().getFirst().hopCount());
        for(var path:withStory.paths())assertEquals(path.nodes().size(),path.nodes().stream().map(PathNode::uid).distinct().count());
    }
    @Test void budgetsCancellationAndResultLimitDoNotInventExactTotals() {
        var p=graph(List.of(c("a"),c("z"),v("p")),List.of(voice("1","p","a",1),voice("2","p","z",2)));
        var q=query("a",1,"z",2);var stopped=ConnectionSearchEngine.search("q",p,q,new ConnectionSearchEngine.Budget(3000,3000,1,100),()->false,r->{});assertEquals("STATE_BUDGET",stopped.stopReason());assertNull(stopped.exactPathCount());assertFalse(stopped.shortestProven());
        assertEquals("CANCELLED",ConnectionSearchEngine.search("q",p,q,ConnectionSearchEngine.Budget.defaults(),()->true,r->{}).status());
        var limited=run(p,new Query(q.from(),q.to(),8,1,false,"CONNECTIVITY",null,false,null,false,false,null,true));assertNull(limited.exactPathCount());assertTrue(limited.shortestProven());assertEquals("RESULT_LIMIT",limited.stopReason());
    }
    @Test void arbitrarySharedWorkAndCompanyEdgesCannotBecomeConnections() {
        var p=graph(List.of(c("a"),c("z"),new Node("company","COMPANY","公司","",List.of())),List.of(fact("illegal","SHARED_WORK","a","z",1,false,false,false,false,null,null)));
        assertTrue(run(p,query("a",1,"z",2)).paths().isEmpty());
    }
    @Test void evidenceDuplicatesMergeWithoutInflatingFacts() {
        var facts=new TreeMap<String,Fact>();var a=voice("1","p","a",1);ConnectionProjectionBuilder.merge(facts,a);ConnectionProjectionBuilder.merge(facts,voice("2","p","a",1));assertEquals(1,facts.size());assertEquals(2,facts.firstEntry().getValue().evidence().size());
    }
    @Test void randomSmallGraphsAgreeWithIndependentExhaustiveDistanceAndPathCounts() {
        var random=new Random(10242);
        for(int trial=0;trial<80;trial++) {
            var nodes=List.of(c("a"),c("b"),c("c"),c("z"),v("p"),v("q"));var facts=new ArrayList<Fact>();
            for(String person:List.of("p","q"))for(String character:List.of("a","b","c","z"))if(random.nextBoolean())facts.add(voice(person+character,person,character,character.equals("a")||character.equals("b")?1:2));
            if(random.nextBoolean())facts.add(story("ab","a","b",1));if(random.nextBoolean())facts.add(story("cz","c","z",2));
            var p=graph(nodes,facts);var q=query("a",1,"z",2);var lengths=new ArrayList<Integer>();enumerate(p,"a",1,null,"z",2,new HashSet<>(Set.of("a")),0,8,lengths);
            var result=run(p,q);assertEquals(lengths.size(),result.returnedPaths());if(!lengths.isEmpty())assertEquals(Collections.min(lengths),result.paths().getFirst().hopCount());assertTrue(result.searchExhausted());
        }
    }
    // Test-only depth-first exhaustive reference, independent of production queue/state representations.
    void enumerate(Projection p,String uid,long work,Long voiceWork,String target,long targetWork,Set<String> seen,int depth,int max,List<Integer> output) {
        if(uid.equals(target)&&work==targetWork){output.add(depth);return;}if(depth==max)return;
        for(var f:p.facts()) {
            String next=f.source().equals(uid)?f.target():f.target().equals(uid)?f.source():null;if(next==null||seen.contains(next))continue;
            long nextWork=work;Long nextVoice=null;
            if(f.kind().equals("STORY")){if(voiceWork!=null||f.subjectId()!=work)continue;}
            else if(next.equals(f.source())){if(f.subjectId()!=work)continue;nextVoice=work;}
            else {if(voiceWork==null||voiceWork==f.subjectId())continue;nextWork=f.subjectId();}
            var visited=new HashSet<>(seen);visited.add(next);enumerate(p,next,nextWork,nextVoice,target,targetWork,visited,depth+1,max,output);
        }
    }
}
