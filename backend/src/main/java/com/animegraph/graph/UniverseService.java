package com.animegraph.graph;

import com.animegraph.common.ApiException;
import com.animegraph.explore.OnlineNetworkService;
import com.animegraph.identity.ExternalIdentityMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service @Order(20)
public class UniverseService implements ApplicationRunner {
    public record Subject(long id,String title,int order,String sourceUrl){}
    public record World(String uid,String title,String summary,String seriesUid,List<Subject> subjects){}
    public record Task(String status,int processed,String error){}
    public record Network(World world,List<Map<String,Object>> nodes,List<Map<String,Object>> edges,boolean truncated,String notice,String version,Task task){}
    private final GraphStore graph;private final OnlineNetworkService online;private final ExternalIdentityMapper identities;private final ObjectMapper json;
    private List<World> worlds=List.of();private List<Map<String,Object>> seeds=List.of();
    private final Map<String,Task> tasks=new ConcurrentHashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"universe-refresh");t.setDaemon(true);return t;});
    public UniverseService(GraphStore graph,OnlineNetworkService online,ExternalIdentityMapper identities,ObjectMapper json){this.graph=graph;this.online=online;this.identities=identities;this.json=json;}
    public void run(ApplicationArguments args)throws Exception{
        try(var stream=new ClassPathResource("identity/worlds.json").getInputStream()){
            var root=json.readTree(stream);worlds=json.convertValue(root.path("worlds"),new TypeReference<List<World>>(){});seeds=json.convertValue(root.path("memberships"),new TypeReference<List<Map<String,Object>>>(){});
            for(var identity:root.path("identities"))identities.seed(json.convertValue(identity,new TypeReference<Map<String,Object>>(){}));
        }
        for(var w:worlds)graph.write("MERGE (w:Universe {uid:$uid}) SET w.title=$title,w.summary=$summary MERGE (s:Series {uid:$series}) SET s.title=$title MERGE (s)-[:SET_IN]->(w)",Map.of("uid",w.uid(),"title",w.title(),"summary",w.summary(),"series",w.seriesUid()));
    }
    @PreDestroy void close(){worker.shutdownNow();try{worker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    public List<World> list(){return worlds;}
    public World world(String uid){return worlds.stream().filter(w->w.uid().equals(uid)||w.seriesUid().equals(uid)).findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","世界观或系列尚未收录"));}
    public synchronized Task start(String uid){var w=world(uid);graph.validateSubjects(w.subjects().stream().map(Subject::id).toList());var old=tasks.get(w.uid());if(old!=null&&Set.of("PENDING","RUNNING").contains(old.status()))return old;var task=new Task("PENDING",0,null);tasks.put(w.uid(),task);worker.execute(()->refresh(w));return task;}
    private void refresh(World world){int processed=0;String generation=UUID.randomUUID().toString();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        try{
            var nodes=new LinkedHashMap<String,OnlineNetworkService.Node>();var appearances=new ArrayList<Map<String,Object>>();var edges=new LinkedHashMap<String,Map<String,Object>>();
            for(var subject:world.subjects()){
                if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())break;
                tasks.put(world.uid(),new Task("RUNNING",processed,null));
                var roster=online.network(subject.id(),"CHARACTERS",1);if(roster.meta().stale())throw new IllegalStateException("来源过期");
                graph.write("MATCH (w:Universe {uid:$world}), (s:Series {uid:$series}) MERGE (a:Entity:Anime {uid:$uid}) ON CREATE SET a.name=$title,a.nameCn=$title,a.source='bangumi',a.sourceId=$id,a.active=true MERGE (a)-[m:IN_UNIVERSE]->(w) SET m.sourceUrl=$url MERGE (a)-[r:MEMBER_OF_SERIES]->(s) SET r.order=$order,r.sourceUrl=$url",Map.of("world",world.uid(),"series",world.seriesUid(),"uid","anime:bangumi:"+subject.id(),"title",subject.title(),"id",String.valueOf(subject.id()),"url",subject.sourceUrl(),"order",subject.order()));
                for(var n:roster.nodes())if(nodes.size()<240||nodes.containsKey(n.id())){nodes.put(n.id(),n);appearances.add(Map.of("id",n.id(),"subjectId",subject.id(),"subjectUid","anime:bangumi:"+subject.id(),"role",n.role(),"sourceUrl","https://bgm.tv/subject/"+subject.id()+"/characters"));}
            }
            for(var n:nodes.values()){
                if(processed>=30||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())break;
                long id=Long.parseLong(n.id().substring(n.id().lastIndexOf(':')+1));
                var relations=online.relations(id);if(relations.meta().stale())throw new IllegalStateException("关系来源过期");
                for(var e:relations.edges())if(nodes.containsKey(e.source())&&nodes.containsKey(e.target())){
                    var row=new LinkedHashMap<String,Object>();row.put("key",e.id());row.put("source",e.source());row.put("target",e.target());row.put("relationType",e.relationType());row.put("label",e.label());row.put("directed",e.directed());row.put("spoiler",e.spoiler());row.put("ended",e.ended());row.put("description",e.description());row.put("evidence",e.evidence());row.put("sourceUrl",e.sourceUrl());row.put("sourceName",e.sourceName());row.put("provider","BANGUMI");row.put("sourceRevision",relations.meta().fetchedAt().toString());
                    edges.put(e.id()+"|"+e.sourceUrl(),row);
                }
                processed++;tasks.put(world.uid(),new Task("RUNNING",processed,null));
            }
            for(var seed:seeds)if(seed.get("worldUid").equals(world.uid())&&nodes.containsKey(seed.get("source"))&&nodes.containsKey(seed.get("target"))){var row=new LinkedHashMap<>(seed);row.put("key","moegirl:membership:"+seed.get("source"));row.put("relationType","MEMBER");row.put("directed",true);row.put("spoiler",false);row.put("ended",false);row.put("description","所属团体（来源全局资料，未限定本季）");row.put("evidence",seed.get("attribution"));row.put("sourceName","萌娘百科（人工核验）");edges.put(String.valueOf(row.get("key")),row);}
            var nodeRows=nodes.values().stream().map(n->{var row=new LinkedHashMap<String,Object>();row.put("uid",n.id());row.put("name",n.originalName());row.put("nameCn",n.label());row.put("imageUrl",n.imageUrl());row.put("characterType",n.characterType());row.put("sourceId",n.id().substring(n.id().lastIndexOf(':')+1));return row;}).toList();
            graph.write("UNWIND $rows AS row MERGE (c:Entity:Character {uid:row.uid}) ON CREATE SET c.name=row.name,c.nameCn=row.nameCn,c.imageUrl=row.imageUrl,c.characterType=row.characterType,c.source='bangumi',c.sourceId=row.sourceId,c.active=true",Map.of("rows",nodeRows));
            graph.write("UNWIND $rows AS row MATCH (c:Entity:Character {uid:row.id}),(a:Entity:Anime {uid:row.subjectUid}) MERGE (c)-[r:WORLD_APPEARANCE {worldUid:$world,subjectId:row.subjectId,generation:$generation}]->(a) SET r.role=row.role,r.sourceUrl=row.sourceUrl",Map.of("rows",appearances,"world",world.uid(),"generation",generation));
            var factRows=new ArrayList<Map<String,Object>>();for(var e:edges.values()){var row=new LinkedHashMap<>(e);String assertion="assertion:"+hash(List.of(e.get("source"),e.get("target"),e.get("relationType"),e.get("directed"),e.get("ended"),"GLOBAL"));row.put("uid",assertion);row.put("evidenceUid","evidence:"+hash(List.of(assertion,e.get("sourceUrl"))));factRows.add(row);}
            graph.write("UNWIND $rows AS row MATCH (s:Entity:Character {uid:row.source}),(t:Entity:Character {uid:row.target}),(w:Universe {uid:$world}) MERGE (a:RelationAssertion {uid:row.uid}) ON CREATE SET a.active=true,a.scopeType='GLOBAL',a.relationType=row.relationType,a.label=row.label,a.directed=row.directed,a.ended=row.ended SET a.spoiler=coalesce(a.spoiler,false) OR row.spoiler MERGE (a)-[:FROM_CHARACTER]->(s) MERGE (a)-[:TO_CHARACTER]->(t) MERGE (a)-[o:OBSERVED_IN {generation:$generation}]->(w) MERGE (e:Evidence {uid:row.evidenceUid}) ON CREATE SET e.active=true,e.provider=row.provider,e.sourceUrl=row.sourceUrl,e.sourceName=row.sourceName,e.sourceRevision=row.sourceRevision,e.description=row.description,e.attribution=row.evidence,e.retrievedAt=$now MERGE (a)-[:SUPPORTED_BY]->(e)",Map.of("rows",factRows,"world",world.uid(),"generation",generation,"now",Instant.now().toString()));
            if(Thread.currentThread().isInterrupted())return;
            graph.write("MATCH (w:Universe {uid:$uid}) SET w.activeGeneration=$generation,w.updatedAt=$now,w.partial=true",Map.of("uid",world.uid(),"generation",generation,"now",Instant.now().toString()));
            tasks.put(world.uid(),new Task("PARTIAL",processed,"已保存有界样本：每作首60名角色、每轮最多30个角色的关系，非完整世界观"));
        }catch(Exception e){tasks.put(world.uid(),new Task("FAILED",processed,e instanceof ApiException?e.getMessage():"本轮来源读取或图写入失败，保留上次可用快照"));}
    }
    private String hash(Object value){try{return GraphStore.hash(json.writeValueAsBytes(value));}catch(Exception e){throw new IllegalStateException(e);}}
    public Network network(String uid,List<Long> subjects,boolean spoilers,String relation,String faction,boolean strict){return readNetwork(uid,subjects,spoilers,relation,faction,strict,0);}
    private Network readNetwork(String uid,List<Long> subjects,boolean spoilers,String relation,String faction,boolean strict,int attempt){var w=world(uid);if(subjects.size()>10||subjects.stream().anyMatch(id->w.subjects().stream().noneMatch(s->s.id()==id)))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","作品筛选超出当前世界观");
        var state=graph.read("MATCH (w:Universe {uid:$uid}) RETURN w.activeGeneration AS generation,coalesce(w.evidenceRevision,0) AS revision LIMIT 1",Map.of("uid",w.uid()));String generation=state.isEmpty()?"":Objects.toString(state.getFirst().get("generation"),"");String version=generation+"|evidence:"+(state.isEmpty()?0:state.getFirst().get("revision"));
        var rows=graph.read("MATCH (w:Universe {uid:$uid}) MATCH (c:Entity:Character)-[r:WORLD_APPEARANCE]->(:Anime) WHERE r.worldUid=w.uid AND r.generation=$generation AND c.active=true AND (size($subjects)=0 OR r.subjectId IN $subjects) WITH c,collect(DISTINCT r.subjectId) AS subjects,collect(DISTINCT r.role) AS roles RETURN properties(c) AS node,subjects,roles LIMIT 241",Map.of("uid",w.uid(),"subjects",subjects,"generation",generation));
        var nodes=new ArrayList<Map<String,Object>>();for(var row:rows.stream().limit(240).toList()){var n=GraphStore.castMap(row.get("node"));var mapped=new LinkedHashMap<String,Object>();mapped.put("id",n.get("uid"));mapped.put("label",Objects.toString(n.get("nameCn"),"").isBlank()?Objects.toString(n.get("name"),"未命名角色"):n.get("nameCn"));mapped.put("originalName",n.getOrDefault("name",""));mapped.put("aliases",n.getOrDefault("aliases",List.of()));mapped.put("imageUrl",n.getOrDefault("imageUrl",""));mapped.put("characterType",n.getOrDefault("characterType",1));mapped.put("subjects",row.get("subjects"));mapped.put("role",String.join("、",((List<?>)row.get("roles")).stream().map(String::valueOf).toList()));mapped.put("group","");mapped.put("description","");nodes.add(mapped);}
        var ids=nodes.stream().map(n->n.get("id")).toList();
        var edgeRows=graph.read("MATCH (w:Universe {uid:$uid})<-[o:OBSERVED_IN]-(a:RelationAssertion)-[:FROM_CHARACTER]->(s:Entity:Character) MATCH (a)-[:TO_CHARACTER]->(t:Entity:Character) WHERE (o.generation=$generation OR o.generation='REVIEWED') AND a.active=true AND s.uid IN $ids AND t.uid IN $ids AND ($spoilers OR a.spoiler=false) AND ($relation='' OR a.relationType=$relation) AND (NOT $strict OR a.scopeType='SUBJECT_EXPLICIT') AND (a.scopeType='GLOBAL' OR size($subjects)=0 OR any(subject IN a.subjectIds WHERE subject IN $subjects)) MATCH (a)-[:SUPPORTED_BY]->(e:Evidence) WHERE e.active=true WITH a,s,t,collect(DISTINCT properties(e)) AS evidence RETURN properties(a) AS edge,s.uid AS source,t.uid AS target,evidence LIMIT 401",Map.of("uid",w.uid(),"ids",ids,"spoilers",spoilers,"relation",relation,"strict",strict,"generation",generation,"subjects",subjects));
        var edges=new ArrayList<Map<String,Object>>();for(var row:edgeRows.stream().limit(400).toList()){var a=GraphStore.castMap(row.get("edge"));var evidence=(List<?>)row.get("evidence");var first=GraphStore.castMap(evidence.getFirst());var e=new LinkedHashMap<>(a);e.put("id",a.get("uid"));e.put("source",row.get("source"));e.put("target",row.get("target"));e.put("evidence",Objects.toString(first.get("attribution"),""));e.put("sourceName",Objects.toString(first.get("sourceName"),""));e.put("sourceUrl",Objects.toString(first.get("sourceUrl"),""));e.put("description",Objects.toString(first.get("description"),""));e.put("evidences",evidence);edges.add(e);}
        if(!faction.isBlank()){var allowed=new HashSet<Object>();allowed.add(faction);edges.stream().filter(e->e.get("relationType").equals("MEMBER")&&Boolean.TRUE.equals(e.get("directed"))&&e.get("target").equals(faction)).forEach(e->allowed.add(e.get("source")));nodes.removeIf(n->!allowed.contains(n.get("id")));edges.removeIf(e->!allowed.contains(e.get("source"))||!allowed.contains(e.get("target")));}
        
        var verified=graph.read("MATCH (w:Universe {uid:$uid}) RETURN w.activeGeneration AS generation,coalesce(w.evidenceRevision,0) AS revision LIMIT 1",Map.of("uid",w.uid()));String after=(verified.isEmpty()?"":Objects.toString(verified.getFirst().get("generation"),""))+"|evidence:"+(verified.isEmpty()?0:verified.getFirst().get("revision"));
        if(!version.equals(after)){if(attempt==0)return readNetwork(uid,subjects,spoilers,relation,faction,strict,1);throw new ApiException(HttpStatus.CONFLICT,"WORLD_CHANGED","关系资料正在更新，请重新读取图谱");}
        return new Network(w,nodes,edges,rows.size()>240||edgeRows.size()>400,"当前为有界公共资料样本；关系默认是全局证据。选择某季仅筛出该季名册角色，不证明关系发生于本季。未标剧透的来源关系仍可能涉及后续内容。",version,tasks.get(w.uid()));
    }
}

