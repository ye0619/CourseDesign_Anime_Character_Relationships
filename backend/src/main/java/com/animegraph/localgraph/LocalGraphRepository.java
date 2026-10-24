package com.animegraph.localgraph;

import static com.animegraph.localgraph.LocalGraphModels.*;
import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

/** All records use separate labels. No write ever matches an official Entity. */
@Repository
public class LocalGraphRepository {
    private final Driver driver;private final String database;private final ObjectMapper json;
    public LocalGraphRepository(Driver driver,@Value("${app.neo4j.database}")String database,ObjectMapper json){this.driver=driver;this.database=database;this.json=json;}
    public Map<String,Entity> currentEntities(List<String> ids) {
        if(ids.isEmpty())return Map.of();if(ids.size()>1200)throw LocalGraphRules.bad("本次本地实体过多");
        try(var session=driver.session(SessionConfig.forDatabase(database))){return session.executeRead(tx->{var result=new HashMap<String,Entity>();for(var row:tx.run("MATCH (n:LocalGraphEntity) WHERE n.uid IN $ids RETURN n.payload AS payload LIMIT 1201",Map.of("ids",ids)).list()){var entity=decode(row.get("payload").asString(),Entity.class);result.put(entity.id(),entity);}return result;},TransactionConfig.builder().withTimeout(Duration.ofSeconds(5)).build());}
    }
    public Document read(String work) {
        try(var session=driver.session(SessionConfig.forDatabase(database))){return session.executeRead(tx->load(tx,work),TransactionConfig.builder().withTimeout(Duration.ofSeconds(5)).build());}
    }
    private Document load(TransactionContext tx,String work) {
        var rows=tx.run("MATCH (w:LocalGraphWorkspace {uid:$work}) RETURN w.version AS version,coalesce(w.schemaVersion,1) AS schemaVersion",Map.of("work",work));
        if(!rows.hasNext())return new Document(0,1,work,List.of(),List.of());
        var workspace=rows.single();long version=workspace.get("version").asLong();int schema=workspace.get("schemaVersion").asInt();
        var nodes=tx.run("MATCH (n:LocalGraphEntity {workUid:$work}) RETURN n.payload AS payload ORDER BY n.uid",Map.of("work",work))
                .list(row->decode(row.get("payload").asString(),Entity.class));
        var edges=tx.run("MATCH ()-[r:LOCAL_GRAPH_LINK {workUid:$work}]->() RETURN r.payload AS payload ORDER BY r.uid",Map.of("work",work))
                .list(row->decode(row.get("payload").asString(),Link.class));
        return new Document(version,schema,work,nodes,edges);
    }
    public Document replace(Document next) {
        // Lock the workspace before revision check. Competing app instances serialize here too.
        try(var session=driver.session(SessionConfig.forDatabase(database))){return session.executeWrite(tx->{
            String work=next.workUid(),now=Instant.now().toString();var params=Map.<String,Object>of("work",work,"now",now);
            tx.run("MERGE (w:LocalGraphWorkspace {uid:$work}) ON CREATE SET w.version=0 SET w.lock=coalesce(w.lock,0)+1",params).consume();
            var previous=load(tx,work);
            if(previous.version()!=next.version())throw new ApiException(HttpStatus.CONFLICT,"LOCAL_GRAPH_CONFLICT","其他窗口已更新本地图谱，请重新读取后再编辑");
            String backup=UUID.randomUUID().toString();
            tx.run("CREATE (b:LocalGraphBackup {uid:$id,workUid:$work,payload:$payload,createdAt:$now})",Map.of("id",backup,"work",work,"payload",encode(previous),"now",now)).consume();
            tx.run("MATCH (n:LocalGraphEntity {workUid:$work}) DETACH DELETE n",params).consume();
            tx.run("MATCH (n:LocalGraphAnchor {workUid:$work}) DETACH DELETE n",params).consume();
            for(var node:next.nodes()) {
                tx.run("CREATE (n:LocalGraphEntity {uid:$uid,workUid:$work,kind:$kind,name:$name,source:'LOCAL',payload:$payload}) WITH n MATCH (w:LocalGraphWorkspace {uid:$work}) CREATE (w)-[:HAS_LOCAL_ENTITY]->(n)",
                        Map.of("uid",node.id(),"work",work,"kind",node.kind(),"name",node.name().strip(),"payload",encode(node))).consume();
            }
            var localIds=new HashSet<>(next.nodes().stream().map(Entity::id).toList());
            var anchors=new HashSet<String>();for(var edge:next.edges())for(var id:List.of(edge.source(),edge.target()))if(!localIds.contains(id))anchors.add(id);
            for(var id:anchors)tx.run("CREATE (:LocalGraphAnchor {uid:$uid,workUid:$work,externalUid:$external,source:'BANGUMI_REFERENCE'})",Map.of("uid",work+"|"+id,"work",work,"external",id)).consume();
            for(var edge:next.edges()) {
                String source=localIds.contains(edge.source())?edge.source():work+"|"+edge.source();String target=localIds.contains(edge.target())?edge.target():work+"|"+edge.target();
                tx.run("MATCH (a),(b) WHERE (a:LocalGraphEntity OR a:LocalGraphAnchor) AND (b:LocalGraphEntity OR b:LocalGraphAnchor) AND a.uid=$source AND b.uid=$target AND a.workUid=$work AND b.workUid=$work CREATE (a)-[:LOCAL_GRAPH_LINK {uid:$uid,workUid:$work,source:'LOCAL',payload:$payload}]->(b)",
                        Map.of("source",source,"target",target,"work",work,"uid",edge.id(),"payload",encode(edge))).consume();
            }
            tx.run("MATCH (w:LocalGraphWorkspace {uid:$work}) SET w.version=w.version+1,w.updatedAt=$now,w.schemaVersion=$schema",Map.of("work",work,"now",now,"schema",next.schemaVersion())).consume();
            tx.run("MATCH (b:LocalGraphBackup {workUid:$work}) WITH b ORDER BY b.createdAt DESC,b.uid DESC SKIP 20 DELETE b",params).consume();
            return new Document(previous.version()+1,next.schemaVersion(),work,next.nodes().stream().sorted(Comparator.comparing(Entity::id)).toList(),next.edges().stream().sorted(Comparator.comparing(Link::id)).toList());
        },TransactionConfig.builder().withTimeout(Duration.ofSeconds(15)).build());}
    }
    public List<Map<String,Object>> backups(String work) {
        try(var session=driver.session(SessionConfig.forDatabase(database))){return session.executeRead(tx->tx.run("MATCH (b:LocalGraphBackup {workUid:$work}) RETURN b.uid AS id,b.createdAt AS createdAt ORDER BY createdAt DESC LIMIT 20",Map.of("work",work)).list(row->row.asMap()));}
    }
    public Document backup(String work,String id) {
        try(var session=driver.session(SessionConfig.forDatabase(database))){return session.executeRead(tx->{
            var result=tx.run("MATCH (b:LocalGraphBackup {workUid:$work,uid:$id}) RETURN b.payload AS payload",Map.of("work",work,"id",id));
            if(!result.hasNext())throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","备份不存在");return decode(result.single().get("payload").asString(),Document.class);
        });}
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private <T>T decode(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Invalid local graph payload",e);}}
}
