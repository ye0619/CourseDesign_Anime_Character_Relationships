package com.animegraph.explore;

import static com.animegraph.charactergraph.CharacterGraphModels.*;
import com.animegraph.charactergraph.*;
import com.animegraph.catalog.CatalogService;
import com.animegraph.common.ApiException;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** User-pinned work snapshots in SQL; character relations remain authoritative in Neo4j. */
@Service
public class SavedAnimeService implements ApplicationRunner {
    public record Payload(Map<String,Object> entity,List<CreditModels.Page> cast,List<CreditModels.Page> staff,
                          String relationStatus,String archiveRevision) {}
    public record Status(boolean saved,String status,String error,Instant updatedAt) {}
    private final SavedAnimeMapper mapper;
    private final BangumiAnimeService anime;
    private final SubjectMetadataService metadata;
    private final MetadataMapper publicMetadata;
    private final CharacterRelationProvider relations;
    private final CharacterGraphService graphs;
    private final GraphStore graph;
    private final CatalogService catalog;
    private final ObjectMapper json;
    private final Object[] locks=new Object[128];
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"save-anime");t.setDaemon(true);return t;});
    public SavedAnimeService(SavedAnimeMapper mapper,BangumiAnimeService anime,SubjectMetadataService metadata,
                             CharacterRelationProvider relations,CharacterGraphService graphs,GraphStore graph,CatalogService catalog,ObjectMapper json,MetadataMapper publicMetadata) {
        this.mapper=mapper;this.anime=anime;this.metadata=metadata;this.relations=relations;this.graphs=graphs;this.graph=graph;this.catalog=catalog;this.json=json;
        this.publicMetadata=publicMetadata;
        Arrays.setAll(locks,i->new Object());
    }
    public void run(ApplicationArguments args){mapper.recover();}
    @PreDestroy void close(){worker.shutdownNow();try{worker.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private void validate(long id){if(id<1||id>Integer.MAX_VALUE)throw BangumiAnimeService.bad("作品编号无效");}
    public Payload read(long id) {
        validate(id);var row=mapper.read(id);if(row==null||row.payload()==null)return null;
        try{return json.readValue(row.payload(),Payload.class);}catch(Exception e){throw new IllegalStateException("Invalid saved anime",e);}
    }
    public Status status(long id){validate(id);var row=mapper.read(id);return row==null?new Status(false,"NOT_SAVED",null,null):new Status(row.payload()!=null,row.status(),row.errorMessage(),row.updatedAt());}
    public Status start(long id,boolean refresh) {
        validate(id);
        synchronized(locks[Math.floorMod(Long.hashCode(id),locks.length)]) {
            var old=status(id);
            if(Set.of("PENDING","RUNNING").contains(old.status())||old.saved()&&!refresh)return old;
            if(worker.getQueue().remainingCapacity()==0)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"SAVE_BUSY","保存队列繁忙，请稍后重试");
            mapper.request(id);
            try{worker.execute(()->save(id));}catch(RejectedExecutionException e){mapper.status(id,"FAILED","保存队列繁忙，请重试");}
            return status(id);
        }
    }
    public Map<String,Object> deleteLocal(String uid) {
        if(uid==null || !uid.matches("anime:[a-z][a-z0-9_-]{0,29}:[A-Za-z0-9_.-]{1,120}")) throw BangumiAnimeService.bad("本地作品编号无效");
        String source=uid.split(":",3)[1];
        Long id=null;
        if(source.equals("bangumi")) {
            try {id=Long.valueOf(uid.substring("anime:bangumi:".length()));validate(id);}
            catch(NumberFormatException e){throw BangumiAnimeService.bad("作品编号无效");}
            if(!uid.equals("anime:bangumi:"+id))throw BangumiAnimeService.bad("作品编号无效");
        }
        synchronized(locks[id==null?Math.floorMod(uid.hashCode(),locks.length):Math.floorMod(Long.hashCode(id),locks.length)]) {
            if(mapper.activeImports(source)>0)throw new ApiException(HttpStatus.CONFLICT,"IMPORT_BUSY","该来源正在导入，请完成后再删除");
            Map<String,Object> archive=id==null?Map.of():mapper.deletionSnapshot(id);
            if(archive==null)archive=Map.of();
            if(Set.of("PENDING","RUNNING").contains(String.valueOf(archive.get("status"))))throw new ApiException(HttpStatus.CONFLICT,"SAVE_BUSY","作品正在保存，请完成后再删除");
            var result=graph.deleteLocalWork(uid,archive);
            // Neo4j backup includes the complete SQL row. If SQL removal fails, retry is safe.
            if(id!=null)mapper.delete(id);
            return result;
        }
    }
    private void save(long id) {
        mapper.status(id,"RUNNING",null);
        try {
            var old=read(id);var notes=new ArrayList<String>();
            var remote=anime.detail(id);
            if(remote.stale())throw new ApiException(HttpStatus.BAD_GATEWAY,"STALE_SOURCE","在线资料暂不可用，已保存内容保留");
            var entity=new LinkedHashMap<>(remote.data());
            // Source refreshes cannot overwrite manually corrected names or fields.
            try {var local=catalog.detail("ANIME","anime:bangumi:"+id);if(local.get("manualFields") instanceof List<?> fields)for(var field:fields)if(local.containsKey(field))entity.put(String.valueOf(field),local.get(field));}
            catch(ApiException e){if(e.status()!=HttpStatus.NOT_FOUND)throw e;}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
            var cast=collect(id,"CAST",old==null?List.of():old.cast(),notes,deadline);
            var staff=collect(id,"STAFF",old==null?List.of():old.staff(),notes,deadline);
            var nodes=nodes(cast);var result=relations.relations(id,nodes.keySet());
            if(!Set.of("VERIFIED","EMPTY").contains(result.status()))notes.add(result.notice());
            var payload=new Payload(entity,cast,staff,result.status(),result.revision());
            // SQL first: even a partial graph write leaves the acquired work/cast/staff recoverable.
            mapper.save(id,json.writeValueAsString(payload),"RUNNING",null);
            if(!cast.isEmpty())publicMetadata.save(id,"CAST",json.writeValueAsString(cast));
            if(!staff.isEmpty())publicMetadata.save(id,"STAFF",json.writeValueAsString(staff));
            writeGraph(id,entity,nodes,result.edges());
            mapper.status(id,notes.isEmpty()?"COMPLETE":"PARTIAL",notes.isEmpty()?null:String.join("；",notes).substring(0,Math.min(480,String.join("；",notes).length())));
        } catch(Exception e) {
            boolean saved=false;try{saved=status(id).saved();}catch(Exception ignored){}
            mapper.status(id,saved?"PARTIAL":"FAILED",e instanceof ApiException?e.getMessage():"保存未全部完成，已获取内容保留；可重新更新恢复");
        }
    }
    private List<CreditModels.Page> collect(long id,String kind,List<CreditModels.Page> previous,List<String> notes,long deadline) {
        var pages=new ArrayList<CreditModels.Page>();
        try {
            for(int page=1;page<=100;page++) {
                if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IllegalStateException("Deadline");
                var data=metadata.page(id,kind,page);
                if(data.source().stale())throw new IllegalStateException("Stale source");
                if(!pages.isEmpty()&&pages.getFirst().coverage().total()!=data.coverage().total())throw new IllegalStateException("Source changed");
                pages.add(data);
                if(data.coverage().nextPage()==null)break;
                Thread.sleep(350);
            }
            if(!SubjectSnapshotService.collected(kind,pages))notes.add(kind+"名册部分内容待补全");
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();notes.add(kind+"名册更新未完成，保留已有分页");if(!previous.isEmpty())return previous;}
        return List.copyOf(pages);
    }
    public CreditModels.Page credits(long id,String kind,int page) {
        validate(id);if(!Set.of("CAST","STAFF").contains(kind)||page<1||page>100)throw BangumiAnimeService.bad("名册分页无效");
        var saved=read(id);var pages=saved==null?List.<CreditModels.Page>of():kind.equals("CAST")?saved.cast():saved.staff();
        var cached=pages.stream().filter(p->p.coverage().page()==page).findFirst();
        if(cached.isPresent())return cached.get();
        // Legacy graph data remains usable without migration or network access.
        try {var local=legacyCredits(id,kind,page);if(local.coverage().total()>0)return local;}catch(ApiException e){if(e.status()!=HttpStatus.NOT_FOUND)throw e;}
        return metadata.page(id,kind,page);
    }
    private CreditModels.Page legacyCredits(long id,String kind,int page) {
        String uid="anime:bangumi:"+id;var data=catalog.related("ANIME",uid,kind.equals("STAFF")?"staff":"cast",page,60);
        var people=new LinkedHashMap<String,CreditModels.Entity>();var characters=new LinkedHashMap<String,CreditModels.Entity>();
        var staff=new ArrayList<CreditModels.StaffCredit>();var cast=new ArrayList<CreditModels.CastCredit>();
        for(var row:data.items()) {
            var person=creditEntity(GraphStore.castMap(row.get("entity")),"PERSON");people.put(person.id(),person);
            var r=GraphStore.castMap(row.get("relation"));
            if(kind.equals("STAFF"))staff.add(new CreditModels.StaffCredit(person.id(),List.of(new CreditModels.Position(text(r,"roleCode"),text(r,"roleName"),text(r,"episodeScope"),text(r,"description")))));
            else if(row.get("character") instanceof Map<?,?>) {
                var character=creditEntity(GraphStore.castMap(row.get("character")),"CHARACTER");characters.put(character.id(),character);
                cast.add(new CreditModels.CastCredit(text(r,"uid"),uid,person.id(),character.id(),null,"配音记录","VOICE",text(r,"language"),null,text(r,"versionLabel"),text(r,"episodeScope"),text(r,"description"),"saved-VOICED"));
            }
        }
        boolean exhausted=page*60L>=data.total();
        return new CreditModels.Page(uid,kind,List.copyOf(people.values()),List.copyOf(characters.values()),staff,cast,
                new CreditModels.Coverage(page,60,(int)data.total(),data.items().size(),data.items().size(),0,0,0,exhausted?null:page+1,exhausted,false,"PARTIAL"),
                new CreditModels.Source("NEO4J","https://bgm.tv/subject/"+id,"saved-records","legacy/1",null,true,false,"legacy/1"),"legacy/1");
    }
    private CreditModels.Entity creditEntity(Map<String,Object> item,String category) {
        String uid=text(item,"uid"),name=text(item,"nameCn");if(name.isBlank())name=text(item,"name");
        return new CreditModels.Entity(uid,name,text(item,"name"),text(item,"imageUrl"),category,item.get("providerType") instanceof Number n?n.intValue():null,List.of(),List.of(),"legacy/1","https://bgm.tv/"+(category.equals("PERSON")?"person/":"character/")+item.get("sourceId"));
    }
    public GraphResponse graph(long id) {
        validate(id);String uid="anime:bangumi:"+id;
        GraphResponse local=null;
        String graphNotice="";
        try{local=graphs.graph(DataSource.NEO4J,uid,200);}catch(ApiException e){if(e.status()!=HttpStatus.NOT_FOUND)graphNotice="已保存的图数据库暂不可用，展示可用名册与来源关系。";}
        // Saved graph is the durable authority. Partial local rosters can still be supplemented.
        var saved=read(id);var nodes=new LinkedHashMap<String,CharacterNode>();var edges=new LinkedHashMap<String,CharacterEdge>();
        if(saved!=null)nodes.putAll(nodes(saved.cast()));
        if(local!=null){local.nodes().forEach(n->nodes.put(n.id(),n));local.edges().forEach(e->edges.put(e.id(),e));}
        boolean rosterTruncated=false;
        if(nodes.isEmpty()) {
            for(int page=1;page<=4;page++) {
                var roster=metadata.page(id,"CAST",page);nodes.putAll(nodes(List.of(roster)));
                rosterTruncated=roster.coverage().total()>nodes.size();
                if(!rosterTruncated||nodes.size()>=200)break;
            }
        }
        var result=relations.relations(id,nodes.keySet());
        var signatures=new HashSet<String>();edges.values().forEach(e->signatures.add(signature(e)));
        result.edges().forEach(e->{if(signatures.add(signature(e)))edges.put(e.id(),e);});
        var visible=nodes.values().stream().limit(200).toList();var ids=new HashSet<>(visible.stream().map(CharacterNode::id).toList());
        var filtered=edges.values().stream().filter(e->ids.contains(e.source())&&ids.contains(e.target())).limit(400).toList();
        String title=local!=null?local.work().title():saved!=null?Objects.toString(saved.entity().getOrDefault("nameCn",saved.entity().get("name")),"动画作品"):"动画作品";
        String notice=graphNotice+result.notice()+" 关系索引状态："+result.status()+"。";
        if(filtered.isEmpty())notice+="当前尚无可用的角色关系资料，可查看名册并稍后更新。";
        boolean truncated=rosterTruncated||nodes.size()>200||edges.size()>400||local!=null&&local.meta().truncated();
        return new GraphResponse(new Work(uid,title,"",nodes.size(),edges.size()),visible,filtered,Arrays.stream(RelationType.values()).map(RelationType::definition).toList(),new Meta(local==null?DataSource.ONLINE:DataSource.NEO4J,false,truncated,notice,200,400,result.status(),result.revision()));
    }
    private LinkedHashMap<String,CharacterNode> nodes(List<CreditModels.Page> pages) {
        var result=new LinkedHashMap<String,CharacterNode>();
        for(var page:pages)for(var node:page.characters())result.put(node.id(),new CharacterNode(node.id(),node.label(),"","角色","",node.originalName(),node.imageUrl(),node.providerType(),node.sourceUrl()));
        return result;
    }
    private String signature(CharacterEdge edge) {
        String from=edge.source(),to=edge.target();
        if(!edge.directed()&&from.compareTo(to)>0){String swap=from;from=to;to=swap;}
        return from+"|"+edge.relationType()+"|"+to;
    }
    private void writeGraph(long id,Map<String,Object> entity,Map<String,CharacterNode> nodes,List<CharacterEdge> edges) {
        String uid="anime:bangumi:"+id,task="saved-anime:"+id;
        var work=new LinkedHashMap<String,Object>();work.put("type","Anime");work.put("sourceId",String.valueOf(id));
        for(String key:List.of("name","nameCn","summary","airDate","year","format","imageUrl"))if(entity.containsKey(key)&&entity.get(key)!=null&&!(entity.get(key) instanceof String s&&s.isBlank()))work.put(key,entity.get(key));
        graph.upsertEntity("bangumi",json.valueToTree(work),task);
        for(var node:nodes.values()) {
            var values=new LinkedHashMap<String,Object>();values.put("type","Character");values.put("sourceId",node.id().substring("character:bangumi:".length()));values.put("name",node.originalName()==null||node.originalName().isBlank()?node.label():node.originalName());
            if(!node.label().equals(values.get("name")))values.put("nameCn",node.label());
            if(node.imageUrl()!=null&&!node.imageUrl().isBlank())values.put("imageUrl",node.imageUrl());
            graph.upsertEntity("bangumi",json.valueToTree(values),task);
            graph.upsertRelation("bangumi",json.valueToTree(Map.of("type","APPEARS_IN","fromUid",node.id(),"toUid",uid,"properties",Map.of())),task);
        }
        for(var edge:edges) {
            var properties=new LinkedHashMap<String,Object>();properties.put("animeUid",uid);properties.put("relationCode",edge.relationType().name());properties.put("label",edge.label());properties.put("evidence",edge.evidence().substring(0,Math.min(1000,edge.evidence().length())));properties.put("sourceName",edge.sourceName());properties.put("sourceUrl",edge.sourceUrl());properties.put("spoiler",edge.spoiler());properties.put("ended",edge.ended());properties.put("directed",edge.directed());
            if(edge.sourceRelationId()!=null)properties.put("sourceRelationId",edge.sourceRelationId());
            if(edge.archiveRevision()!=null)properties.put("archiveRevision",edge.archiveRevision());
            graph.upsertRelation("bangumi",json.valueToTree(Map.of("type","CHARACTER_RELATION","fromUid",edge.source(),"toUid",edge.target(),"properties",properties)),task);
        }
    }
    private String text(Map<String,Object> data,String key){return Objects.toString(data.get(key),"");}
}
