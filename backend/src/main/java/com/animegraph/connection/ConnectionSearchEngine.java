package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A* over path histories. Relaxed UID distances are only admissible lower bounds, never visited-state pruning. */
public final class ConnectionSearchEngine {
    public record Budget(long shortestMillis, long moreMillis, int states, int edges) {
        public static Budget defaults() { return new Budget(3000, 3000, 50_000, 200_000); }
    }
    private record State(String uid, long subject, String version, Fact incomingVoice,
                         List<PathNode> nodes, List<Step> steps, Set<String> visited, int voices, int stories, int crosses, String order) {}
    private ConnectionSearchEngine() {}
    public static Result search(String id, Projection graph, Query q, Budget budget, BooleanSupplier cancelled, Consumer<Result> publish) {
        var nodes = new HashMap<String,Node>(); graph.nodes().stream().filter(n->Set.of("CHARACTER","VOICE_ACTOR").contains(n.kind())).forEach(n -> nodes.put(n.uid(),n));
        var adjacent = new HashMap<String,List<Fact>>();
        graph.facts().stream().filter(f->Set.of("VOICE","STORY").contains(f.kind())&&nodes.containsKey(f.source())&&nodes.containsKey(f.target())).sorted(Comparator.comparing(Fact::uid)).forEach(f -> {
            adjacent.computeIfAbsent(f.source(), k -> new ArrayList<>()).add(f);
            adjacent.computeIfAbsent(f.target(), k -> new ArrayList<>()).add(f);
        });
        long started=System.nanoTime(), deadline=started+budget.shortestMillis()*1_000_000;
        // The relaxed graph drops work/version/visited restrictions, so its distance cannot exceed a legal distance.
        // Every legal edge is present in the relaxation; consistent unit-cost lower bounds prove the first goal.
        var distances=new HashMap<String,Integer>();var relaxed=new ArrayDeque<String>();distances.put(q.to().characterUid(),0);relaxed.add(q.to().characterUid());
        long preprocessingChecks=0;
        while(!relaxed.isEmpty()) {
            String uid=relaxed.removeFirst();int distance=distances.get(uid);
            if(distance>=q.maxHops())continue;
            for(var edge:adjacent.getOrDefault(uid,List.of())) {
                if(++preprocessingChecks>budget.edges()||cancelled.getAsBoolean()||System.nanoTime()>deadline)return result(id,graph,q,Map.of(),false,false,cancelled.getAsBoolean()?"CANCELLED":System.nanoTime()>deadline?"TIME_BUDGET":"EDGE_BUDGET",0,preprocessingChecks,started);
                String other=edge.source().equals(uid)?edge.target():edge.source();if(distances.putIfAbsent(other,distance+1)==null)relaxed.addLast(other);
            }
        }
        var queue = new PriorityQueue<State>(Comparator.comparingInt((State s)->s.steps().size()+distances.getOrDefault(s.uid(),q.maxHops()+1)).thenComparing(Comparator.comparingInt((State s)->s.steps().size()).reversed()).thenComparing(State::order));
        var found=new LinkedHashMap<String,Path>();
        var first=nodes.get(q.from().characterUid());
        queue.add(new State(first.uid(),q.from().subjectId(),null,null,List.of(pathNode(first,q.from().subjectId())),List.of(),Set.of(first.uid()),0,0,0,first.uid()));
        long states=1,checks=preprocessingChecks; int shortest=-1; boolean proven=false; String stop="EXHAUSTED";
        outer: while(!queue.isEmpty()) {
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()){stop="CANCELLED";break;}
            if(System.nanoTime()>deadline){stop="TIME_BUDGET";break;}
            var state=queue.remove(); int hops=state.steps().size();
            if(state.uid().equals(q.to().characterUid()) && state.subject()==q.to().subjectId()
                    && (state.stories()>0 || !q.requireStoryRelation())) {
                // Every unexpanded path has cost + admissible remainder at least this goal cost.
                if(shortest<0){shortest=hops;proven=true;deadline=System.nanoTime()+budget.moreMillis()*1_000_000;}
                String signature=state.steps().stream().map(s->s.traversalSource()+">"+s.fact().uid()+">"+s.traversalTarget()).reduce("",(a,b)->a+"|"+b);
                found.putIfAbsent(signature,new Path(signature,state.nodes(),state.steps(),hops,state.voices(),state.stories(),state.crosses()));
                publish.accept(result(id,graph,q,found,false,proven,"RUNNING",states,checks,started));
                if(found.size()>=q.limit()){stop="RESULT_LIMIT";break;}
                continue;
            }
            if(hops>=q.maxHops())continue;
            for(var f:adjacent.getOrDefault(state.uid(),List.of())) {
                if(checks++>=budget.edges()){stop="EDGE_BUDGET";break outer;}
                if(cancelled.getAsBoolean()){stop="CANCELLED";break outer;}
                if(System.nanoTime()>deadline){stop="TIME_BUDGET";break outer;}
                if(f.spoiler()&&!q.spoilers() || f.ended()&&!q.includeEnded() || f.manual()&&!q.includeManualRelations())continue;
                String next=f.source().equals(state.uid())?f.target():f.source();
                if(state.visited().contains(next))continue;
                Node node=nodes.get(next); if(node==null)continue;
                boolean voice=f.kind().equals("VOICE"); int crosses=state.crosses(); String version=state.version();
                Fact incoming=null; long subject=state.subject();
                if(voice) {
                    if(q.language()!=null&&!q.language().isBlank()&&!Objects.equals(q.language(),f.language())
                            && !(q.includeUnknownLanguage()&&(f.language()==null||f.language().isBlank())))continue;
                    if(q.version()!=null&&!q.version().isBlank()&&!Objects.equals(q.version(),f.version()))continue;
                    if(node.kind().equals("VOICE_ACTOR")) {
                        if(f.subjectId()!=subject || !compatible(version,f.version(),state.steps().isEmpty()))continue;
                        incoming=f;
                    } else {
                        if(state.incomingVoice()==null || state.incomingVoice().subjectId()==f.subjectId())continue;
                        subject=f.subjectId();version=empty(f.version());crosses++;
                    }
                } else {
                    if(!node.kind().equals("CHARACTER")||state.incomingVoice()!=null||f.subjectId()!=subject)continue;
                    if("STRICT".equals(q.storyDirection())&&f.directed()&&!f.source().equals(state.uid()))continue;
                    if(!compatible(version,f.version(),state.steps().isEmpty()))continue;
                    version=empty(f.version());
                }
                if(hops+1+distances.getOrDefault(next,q.maxHops()+1)>q.maxHops())continue;
                if(++states>budget.states()){stop="STATE_BUDGET";break outer;}
                var visited=new HashSet<>(state.visited());visited.add(next);
                var pathNodes=new ArrayList<>(state.nodes());pathNodes.add(pathNode(node,node.kind().equals("CHARACTER")?subject:null));
                var steps=new ArrayList<>(state.steps());steps.add(new Step(f,state.uid(),next,!f.source().equals(state.uid())));
                queue.add(new State(next,subject,version,incoming,List.copyOf(pathNodes),List.copyOf(steps),Set.copyOf(visited),
                        state.voices()+(voice?1:0),state.stories()+(voice?0:1),crosses,state.order()+"|"+f.uid()+"|"+next));
            }
        }
        return result(id,graph,q,found,stop.equals("EXHAUSTED"),proven||stop.equals("EXHAUSTED"),stop,states,checks,started);
    }
    private static String empty(String value){return value==null||value.isBlank()?null:value;}
    private static boolean compatible(String a,String b,boolean initial){return initial||Objects.equals(empty(a),empty(b));}
    private static PathNode pathNode(Node n,Long subject){return new PathNode(n.uid(),n.kind(),n.name(),n.imageUrl(),subject);}
    private static Result result(String id,Projection graph,Query q,Map<String,Path> found,boolean exhausted,boolean proven,String stop,long states,long checks,long started) {
        boolean running=stop.equals("RUNNING"); var paths=List.copyOf(found.values());
        return new Result(id,running?"RUNNING":stop.equals("CANCELLED")?"CANCELLED":"COMPLETE",
                proven?"COMPLETE":running?"RUNNING":"INTERRUPTED",running?"RUNNING":exhausted||stop.equals("RESULT_LIMIT")?"COMPLETE":"INTERRUPTED",q,graph.revision(),paths,
                paths.size(),paths.size(),exhausted?(long)paths.size():null,exhausted,proven,!exhausted,stop,states,checks,
                (System.nanoTime()-started)/1_000_000,graph.subjects());
    }
}
