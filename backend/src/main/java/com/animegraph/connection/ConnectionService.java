package com.animegraph.connection;

import static com.animegraph.connection.ConnectionModels.*;
import com.animegraph.common.ApiException;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConnectionService {
    private static class Task {
        final String owner; final Instant expires=Instant.now().plusSeconds(900); volatile boolean cancelled; volatile Result result;
        Task(String owner){this.owner=owner;}
    }
    private static class Build {
        final String owner; final Instant expires=Instant.now().plusSeconds(900); volatile boolean cancelled; volatile BuildJob result;
        Build(String owner,BuildJob result){this.owner=owner;this.result=result;}
    }
    private final ConnectionProjectionRepository repository;private final ConnectionProjectionBuilder builder;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.animegraph.characteredit.CharacterEditService edits;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.animegraph.localgraph.LocalGraphRepository local;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.animegraph.identity.IdentityNameMapper reviewed;
    private volatile Projection active;
    private final Map<String,Task> tasks=new ConcurrentHashMap<>();private final Map<String,Build> builds=new ConcurrentHashMap<>();
    private final ThreadPoolExecutor searches=pool(2,8,"character-connections"),building=pool(1,1,"connection-projection");
    public ConnectionService(ConnectionProjectionRepository repository,ConnectionProjectionBuilder builder){this.repository=repository;this.builder=builder;}
    private static ThreadPoolExecutor pool(int threads,int queue,String name){return new ThreadPoolExecutor(threads,threads,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(queue),r->{var t=new Thread(r,name);t.setDaemon(true);return t;});}
    @PreDestroy void close(){tasks.values().forEach(t->t.cancelled=true);builds.values().forEach(b->b.cancelled=true);searches.shutdownNow();building.shutdownNow();}
    private synchronized Projection graph(){if(active==null)active=repository.active();return active;}
    public Map<String,Object> coverage() {
        var p=graph();return p==null?Map.of("status","NOT_FETCHED","subjects",List.of(),"characters",0,"voiceActors",0,"voiceFacts",0,"storyFacts",0):Map.of(
                "status","PARTIAL","graphRevision",p.revision(),"builtAt",p.builtAt(),"subjects",p.subjects(),
                "characters",p.nodes().stream().filter(n->n.kind().equals("CHARACTER")).count(),"voiceActors",p.nodes().stream().filter(n->n.kind().equals("VOICE_ACTOR")).count(),
                "voiceFacts",p.facts().stream().filter(f->f.kind().equals("VOICE")).count(),"storyFacts",p.facts().stream().filter(f->f.kind().equals("STORY")).count());
    }
    public List<Candidate> characters(String text,Long subjectId) {
        var p=graph();if(p==null)return List.of();String query=text==null?"":com.animegraph.identity.IdentityNameService.normalize(text);
        if(query.length()>100)throw bad("搜索名称最多100字");
        var nodes=new HashMap<String,Node>();p.nodes().forEach(n->nodes.put(n.uid(),n));var titles=new HashMap<Long,String>();p.subjects().forEach(s->titles.put(s.subjectId(),s.title()));
        var modifications=new HashMap<String,com.animegraph.characteredit.CharacterEditService.Edit>();
        var characterIds=nodes.keySet().stream().filter(uid->uid.startsWith("character:")).sorted().toList();
        if(edits!=null)for(int offset=0;offset<characterIds.size();offset+=1000)modifications.putAll(edits.many(characterIds.subList(offset,Math.min(offset+1000,characterIds.size()))));
        modifications.forEach((uid,edit)->{var node=nodes.get(uid);if(edit.fields().has("imageUrl")){node=new Node(uid,node.kind(),node.name(),edit.fields().get("imageUrl").asText(),node.names());nodes.put(uid,node);}if(edit.fields().has("names")){
            var names=new ArrayList<com.animegraph.identity.NameResolutionService.LocalName>();for(var n:edit.fields().path("names"))names.add(new com.animegraph.identity.NameResolutionService.LocalName(n.path("id").asText(),n.path("text").asText(),n.path("locale").asText(),n.path("kind").asText(),"LOCAL","MANUAL"));
            var labels=new LinkedHashSet<>(node.names());names.forEach(n->labels.add(n.text()));nodes.put(uid,new Node(uid,node.kind(),com.animegraph.identity.NameResolutionService.localDisplay(names,edit.fields().path("preferredNameId").asText(null),List.of()),edit.fields().path("imageUrl").asText(node.imageUrl()),List.copyOf(labels)));
        }});
        if(reviewed!=null)for(int offset=0;offset<characterIds.size();offset+=240){var aliases=reviewed.active(characterIds.subList(offset,Math.min(offset+240,characterIds.size())));if(aliases.size()>4800)throw bad("审核名称过多，请稍后重试");for(var alias:aliases){var node=nodes.get(alias.canonicalUid());if(node!=null){var labels=new LinkedHashSet<>(node.names());labels.add(alias.name());nodes.put(node.uid(),new Node(node.uid(),node.kind(),node.name(),node.imageUrl(),List.copyOf(labels)));}}}
        var localByUid=new HashMap<String,com.animegraph.localgraph.LocalGraphModels.Entity>();
        var localIds=nodes.keySet().stream().filter(uid->uid.startsWith("local:entity:")).sorted().toList();
        if(local!=null)for(int offset=0;offset<localIds.size();offset+=1000)for(var entity:local.currentEntities(localIds.subList(offset,Math.min(offset+1000,localIds.size()))).values()){localByUid.put(entity.id(),entity);var labels=new LinkedHashSet<>(entity.aliases());labels.add(entity.name());if(entity.names()!=null)entity.names().forEach(n->labels.add(n.text()));nodes.put(entity.id(),new Node(entity.id(),"CHARACTER",entity.name(),entity.imageUrl(),List.copyOf(labels)));}
        var voiced=new HashSet<Endpoint>();p.facts().stream().filter(f->f.kind().equals("VOICE")).forEach(f->voiced.add(new Endpoint(f.target(),f.subjectId())));
        return p.appearances().stream().filter(e->subjectId==null||subjectId==e.subjectId()).map(e->{
            var n=nodes.get(e.characterUid());String match=n.uid().equals(query)?n.name():n.names().stream().filter(name->com.animegraph.identity.IdentityNameService.normalize(name).contains(query)).findFirst().orElse(null);
            var edit=modifications.get(n.uid());var entity=localByUid.get(n.uid());var portraits=edit!=null?edit.portraits():entity!=null&&entity.portraits()!=null?entity.portraits():Map.<String,com.fasterxml.jackson.databind.JsonNode>of();var portrait=portraits.get("anime:bangumi:"+e.subjectId());String image=portrait==null?n.imageUrl():portrait.path("imageUrl").asText(n.imageUrl());
            return match==null?null:new Candidate(n.uid(),e.subjectId(),n.name(),image,titles.get(e.subjectId()),n.names(),voiced.contains(e),match,portraits);
        }).filter(Objects::nonNull).sorted(Comparator.comparing(Candidate::characterUid).thenComparingLong(Candidate::subjectId)).limit(100).toList();
    }
    public synchronized Result start(String owner,Query q) {
        cleanup();var graph=graph();if(graph==null)throw new ApiException(HttpStatus.CONFLICT,"COVERAGE_MISSING","请先从已有公开资料构建探索范围");
        if(q.from().characterUid().equals(q.to().characterUid()))throw bad("这是同一角色，请另选终点角色");
        if(q.from().subjectId()==q.to().subjectId())throw bad("请选择不同作品的角色；作品内联系请使用作品关系图谱");
        if(!Set.of("CONNECTIVITY","STRICT").contains(Objects.toString(q.storyDirection(),"CONNECTIVITY")))throw bad("剧情方向条件无效");
        if(q.graphRevision()!=null&&!q.graphRevision().equals(graph.revision()))throw new ApiException(HttpStatus.CONFLICT,"REVISION_CHANGED","资料版本已更新，请重新读取范围后查询");
        if(!graph.appearances().contains(q.from())||!graph.appearances().contains(q.to()))throw bad("端点必须是探索范围内该作品的角色，不能选择声优或组织");
        if(tasks.size()>=64||tasks.values().stream().filter(t->t.owner.equals(owner)&&t.result!=null&&t.result.status().equals("RUNNING")).count()>=4)throw busy();
        String id=UUID.randomUUID().toString();var task=new Task(owner);
        task.result=new Result(id,"RUNNING","RUNNING","PENDING",q,graph.revision(),List.of(),0,0,null,false,false,false,"RUNNING",0,0,0,graph.subjects());tasks.put(id,task);
        try{searches.execute(()->{
            try {var result=ConnectionSearchEngine.search(id,graph,q,ConnectionSearchEngine.Budget.defaults(),()->task.cancelled,r->{synchronized(task){if(!task.cancelled)task.result=r;}});
                synchronized(task){if(!task.cancelled)task.result=result;}
            }catch(Exception e){synchronized(task){if(!task.cancelled)task.result=stopped(task.result,"FAILED","INTERNAL_ERROR");}}
        });}catch(RejectedExecutionException e){tasks.remove(id);throw busy();}
        return task.result;
    }
    public Result read(String owner,String id){var task=tasks.get(id);if(task==null||!task.owner.equals(owner)||task.expires.isBefore(Instant.now()))throw missing();return task.result;}
    public Result cancel(String owner,String id){read(owner,id);var task=tasks.get(id);synchronized(task){if(task.result.status().equals("RUNNING")){task.cancelled=true;task.result=stopped(task.result,"CANCELLED","CANCELLED");}}return task.result;}
    private static Result stopped(Result r,String status,String reason){return new Result(r.queryId(),status,r.shortestProven()?"COMPLETE":"INTERRUPTED","INTERRUPTED",r.query(),r.graphRevision(),r.paths(),r.returnedPaths(),r.foundPathCount(),null,false,r.shortestProven(),true,reason,r.states(),r.edgeChecks(),r.elapsedMillis(),r.scope());}
    public synchronized BuildJob build(String owner,BuildRequest request) {
        cleanup();if(builds.size()>=64||building.getActiveCount()>0||!building.getQueue().isEmpty())throw busy();
        String id=UUID.randomUUID().toString();var build=new Build(owner,new BuildJob(id,"PENDING",0,request.subjectIds()==null?0:request.subjectIds().size(),null,null));builds.put(id,build);
        try {building.execute(()->{
            synchronized(build){if(build.cancelled)return;build.result=new BuildJob(id,"RUNNING",0,build.result.total(),null,null);}
            try {var projection=builder.build(request.subjectIds(),()->build.cancelled,count->{synchronized(build){if(!build.cancelled)build.result=new BuildJob(id,"RUNNING",count,build.result.total(),null,null);}});
                synchronized(build){if(build.cancelled)return;repository.publish(projection);active=projection;build.result=new BuildJob(id,"COMPLETE",projection.subjects().size(),projection.subjects().size(),null,projection.revision());}
            }catch(CancellationException e){build.result=new BuildJob(id,"CANCELLED",build.result.processed(),build.result.total(),null,null);}
            catch(Exception e){build.result=new BuildJob(id,"FAILED",build.result.processed(),build.result.total(),e instanceof ApiException?e.getMessage():"范围构建失败，上一资料版本仍可使用",null);}
        });}catch(RejectedExecutionException e){builds.remove(id);throw busy();}
        return build.result;
    }
    public BuildJob buildStatus(String owner,String id,boolean cancel) {
        var build=builds.get(id);if(build==null||!build.owner.equals(owner)||build.expires.isBefore(Instant.now()))throw missing();
        if(cancel)synchronized(build){if(Set.of("PENDING","RUNNING").contains(build.result.status())){build.cancelled=true;build.result=new BuildJob(id,"CANCELLED",build.result.processed(),build.result.total(),null,null);}}
        return build.result;
    }
    private void cleanup(){var now=Instant.now();tasks.entrySet().removeIf(e->{if(e.getValue().expires.isBefore(now)){e.getValue().cancelled=true;return true;}return false;});builds.entrySet().removeIf(e->e.getValue().expires.isBefore(now)&&!Set.of("RUNNING","PENDING").contains(e.getValue().result.status()));}
    private static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"CONNECTION_INVALID",message);}
    private static ApiException busy(){return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"CONNECTION_BUSY","探索任务繁忙，请稍后重试");}
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"CONNECTION_EXPIRED","任务已过期或不属于当前会话，请重新查询");}
}
