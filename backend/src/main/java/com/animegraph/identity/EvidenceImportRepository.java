package com.animegraph.identity;

import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

/** Batch provenance and evidence are published in ONE Neo4j transaction. */
@Repository
public class EvidenceImportRepository {
    public record NodeState(String uid,Boolean active,List<Long> subjects,String label) {
        public NodeState(String uid,Boolean active,List<Long> subjects){this(uid,active,subjects,uid);}
    }
    public record FactState(String uid,String evidenceUid,Boolean assertionActive,Boolean evidenceActive) {}
    public record State(String generation,long revision,List<String> members,Map<String,Object> batch,List<NodeState> nodes,List<FactState> facts) {
        public List<String> blockers(EvidencePackage p,String hash){
            var errors=new ArrayList<String>();
            if(generation.isEmpty())errors.add("世界观没有已发布名册，请先补充公共资料");
            if(!batch.isEmpty()&&!Objects.equals(hash,batch.get("packageHash")))errors.add("批次编号已用于其他内容，请使用新编号");
            if("DISABLED".equals(batch.get("status")))errors.add("该批次已停用，不能通过重复导入恢复");
            for(var n:nodes)if(!Boolean.TRUE.equals(n.active())||n.subjects().isEmpty())errors.add("角色未启用或不在当前世界观名册："+n.uid());
            for(var f:facts)if(Boolean.FALSE.equals(f.assertionActive())||Boolean.FALSE.equals(f.evidenceActive()))errors.add("关系或证据已撤回，禁止导入恢复："+f.uid());
            for(var f:p.facts())for(long id:f.subjectIds()){
                if(!members.contains("anime:bangumi:"+id))errors.add("关系限定作品不是当前世界观成员："+id);
                for(String uid:List.of(f.source(),f.target()))if(nodes.stream().noneMatch(n->n.uid().equals(uid)&&n.subjects().contains(id)))errors.add("明确作品范围与该作品角色名册不一致："+uid);
            }
            return errors.stream().distinct().toList();
        }
        public String hash(ObjectMapper json){return EvidencePackage.hash(json,this);}
    }
    private final Driver driver;private final String database;private final ObjectMapper json;
    private final LegacyEvidenceScope legacyScope;
    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceImportRepository(Driver driver,@Value("${app.neo4j.database}")String database,ObjectMapper json){this(driver,database,json,new LegacyEvidenceScope(json));}
    EvidenceImportRepository(Driver driver,@Value("${app.neo4j.database}")String database,ObjectMapper json,LegacyEvidenceScope legacyScope){this.driver=driver;this.database=database;this.json=json;this.legacyScope=legacyScope;}
    private Session session(){return driver.session(SessionConfig.forDatabase(database));}
    public State snapshot(EvidencePackage p){try(var s=session()){return s.executeRead(tx->state(tx,p),TransactionConfig.builder().withTimeout(Duration.ofSeconds(5)).build());}}
    private State state(TransactionContext tx,EvidencePackage p){
        var worlds=tx.run("MATCH (w:Universe {uid:$world}) RETURN w.activeGeneration AS generation,coalesce(w.evidenceRevision,0) AS revision",Map.of("world",p.worldUid())).list();
        String generation=worlds.isEmpty()?"":worlds.getFirst().get("generation").asString("");long revision=worlds.isEmpty()?0:worlds.getFirst().get("revision").asLong();
        var members=tx.run("MATCH (a:Entity:Anime)-[:IN_UNIVERSE]->(:Universe {uid:$world}) RETURN a.uid AS uid ORDER BY uid",Map.of("world",p.worldUid())).list(r->r.get("uid").asString());
        for(String member:members) if(!member.startsWith("anime:fixture:")) legacyScope.require(member);
        for(var fact:p.facts()) for(long subject:fact.subjectIds()) legacyScope.require("anime:bangumi:"+subject);
        var batches=tx.run("MATCH (b:EvidenceImportBatch {uid:$uid}) RETURN b{.uid,.packageHash,.status} AS batch",Map.of("uid",p.batchUid())).list();
        var batch=batches.isEmpty()?Map.<String,Object>of():batches.getFirst().get("batch").asMap();
        var ids=p.facts().stream().flatMap(f->java.util.stream.Stream.of(f.source(),f.target())).distinct().sorted().toList();
        var nodes=tx.run("UNWIND $ids AS uid OPTIONAL MATCH (c:Entity:Character {uid:uid}) OPTIONAL MATCH (c)-[r:WORLD_APPEARANCE]->(:Anime) WHERE r.worldUid=$world AND r.generation=$generation RETURN uid,c.active AS active,CASE WHEN coalesce(c.nameCn,'')='' THEN coalesce(c.name,uid) ELSE c.nameCn END AS label,collect(DISTINCT r.subjectId) AS subjects ORDER BY uid",Map.of("ids",ids,"world",p.worldUid(),"generation",generation)).list(r->new NodeState(r.get("uid").asString(),r.get("active").isNull()?null:r.get("active").asBoolean(),r.get("subjects").asList(org.neo4j.driver.Value::asLong).stream().sorted().toList(),r.get("label").asString()));
        var rows=p.facts().stream().map(f->Map.of("uid",f.uid(),"evidenceUid",f.evidenceUid())).toList();
        var facts=tx.run("UNWIND $rows AS row OPTIONAL MATCH (a:RelationAssertion {uid:row.uid}) OPTIONAL MATCH (e:Evidence {uid:row.evidenceUid}) RETURN row.uid AS uid,row.evidenceUid AS evidenceUid,a.active AS assertionActive,e.active AS evidenceActive ORDER BY evidenceUid",Map.of("rows",rows)).list(r->new FactState(r.get("uid").asString(),r.get("evidenceUid").asString(),r.get("assertionActive").isNull()?null:r.get("assertionActive").asBoolean(),r.get("evidenceActive").isNull()?null:r.get("evidenceActive").asBoolean()));
        return new State(generation,revision,members,batch,nodes,facts);
    }
    public Map<String,Object> apply(EvidencePackage p,String expected,long actor){
        try(var s=session()){
            return s.executeWrite(tx->{
                // Lock the world before rechecking the reviewed state; all maintenance
                // operations use this lock even across separate application processes.
                var assertionIds=p.facts().stream().map(EvidencePackage.Fact::uid).distinct().sorted().toList();
                lockWorlds(tx,p.worldUid(),assertionIds);
                var current=state(tx,p);String hash=p.hash(json);
                if("APPLIED".equals(current.batch().get("status"))&&hash.equals(current.batch().get("packageHash")))return batch(tx,p.batchUid());
                if(!current.hash(json).equals(expected)||!current.blockers(p,hash).isEmpty())throw conflict("资料或批次状态已变化，请重新预览");
                String now=Instant.now().toString();var params=new HashMap<String,Object>();params.put("uid",p.batchUid());params.put("hash",hash);params.put("world",p.worldUid());params.put("actor",actor);params.put("now",now);params.put("provider",p.provider());params.put("version",p.sourceVersion());params.put("count",p.facts().size());params.put("payload",encode(p));
                tx.run("CREATE (b:EvidenceImportBatch {uid:$uid,packageHash:$hash,worldUid:$world,status:'APPLIED',createdBy:$actor,createdAt:$now,provider:$provider,sourceVersion:$version,evidenceCount:$count,payload:$payload})",params).consume();
                var rows=p.facts().stream().map(f->{var m=json.convertValue(f,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});m.put("provider",p.provider());m.put("licenseLabel",p.licenseLabel());m.put("retrievedAt",p.retrievedAt());return m;}).toList();
                var written=tx.run("""
                    UNWIND $rows AS row
                    MATCH (s:Entity:Character {uid:row.source}),(t:Entity:Character {uid:row.target}),
                          (w:Universe {uid:$world}),(b:EvidenceImportBatch {uid:$batch})
                    MERGE (a:RelationAssertion {uid:row.uid})
                    ON CREATE SET a.active=true,a.scopeType=row.scopeType,a.subjectIds=row.subjectIds,
                                  a.relationType=row.relationType,a.label=row.label,a.directed=row.directed,a.ended=row.ended
                    SET a.spoiler=coalesce(a.spoiler,false) OR row.spoiler
                    MERGE (a)-[:FROM_CHARACTER]->(s) MERGE (a)-[:TO_CHARACTER]->(t)
                    MERGE (a)-[:OBSERVED_IN {generation:'REVIEWED'}]->(w)
                    MERGE (e:Evidence {uid:row.evidenceUid})
                    ON CREATE SET e.active=true,e.provider=row.provider,e.sourceUrl=row.sourceUrl,
                                  e.sourceName=row.sourceName,e.sourceRevision=row.sourceRevision,
                                  e.sourceField=row.sourceField,e.rawRelation=row.rawRelation,
                                  e.description=row.description,e.attribution=row.attribution,
                                  e.matchMethod=row.matchMethod,e.licenseLabel=row.licenseLabel,e.retrievedAt=row.retrievedAt
                    SET e._reviewLock=true REMOVE e._reviewLock
                    MERGE (a)-[:SUPPORTED_BY]->(e) MERGE (b)-[:INCLUDES_EVIDENCE]->(e)
                    RETURN a.active AS assertionActive,e.active AS evidenceActive
                    """,Map.of("rows",rows,"world",p.worldUid(),"batch",p.batchUid())).list();
                if(written.size()!=p.facts().size()||written.stream().anyMatch(r->!r.get("assertionActive").asBoolean(false)||!r.get("evidenceActive").asBoolean(false)))throw conflict("关系或证据在提交期间已撤回，请重新预览");
                bumpWorlds(tx,assertionIds);
                return batch(tx,p.batchUid());
            },TransactionConfig.builder().withTimeout(Duration.ofSeconds(15)).build());
        }
    }
    public Map<String,Object> disable(String uid,String reason,long actor){
        try(var s=session()){return s.executeWrite(tx->{
            var before=batch(tx,uid);if(before.isEmpty())throw missing();
            var assertions=tx.run("MATCH (b:EvidenceImportBatch {uid:$uid})-[:INCLUDES_EVIDENCE]->(:Evidence)<-[:SUPPORTED_BY]-(a:RelationAssertion) RETURN DISTINCT a.uid AS uid ORDER BY uid",Map.of("uid",uid)).list(r->r.get("uid").asString());
            lockWorlds(tx,String.valueOf(before.get("worldUid")),assertions);
            // Re-read after taking the same world lock used by apply().
            if("DISABLED".equals(batch(tx,uid).get("status")))return batch(tx,uid);
            tx.run("MATCH (:EvidenceImportBatch {uid:$uid})-[:INCLUDES_EVIDENCE]->(e:Evidence) WITH DISTINCT e ORDER BY e.uid SET e._reviewLock=true REMOVE e._reviewLock",Map.of("uid",uid)).consume();
            tx.run("MATCH (b:EvidenceImportBatch {uid:$uid}) SET b.status='DISABLED',b.disabledAt=$now,b.disabledBy=$actor,b.disableReason=$reason WITH b MATCH (b)-[:INCLUDES_EVIDENCE]->(e:Evidence) WHERE NOT EXISTS { MATCH (other:EvidenceImportBatch)-[:INCLUDES_EVIDENCE]->(e) WHERE other.status='APPLIED' } SET e.active=false,e.withdrawnAt=$now",Map.of("uid",uid,"actor",actor,"now",Instant.now().toString(),"reason",reason)).consume();
            bumpWorlds(tx,assertions);return batch(tx,uid);
        },TransactionConfig.builder().withTimeout(Duration.ofSeconds(10)).build());}
    }
    public Map<String,Object> revoke(String uid,String reason,long actor){
        try(var s=session()){return s.executeWrite(tx->{
            // Ordered world locks avoid deadlocks for a shared global assertion.
            tx.run("MATCH (a:RelationAssertion {uid:$uid})-[:OBSERVED_IN]->(w:Universe) WITH DISTINCT w ORDER BY w.uid SET w.evidenceRevision=coalesce(w.evidenceRevision,0)",Map.of("uid",uid)).consume();
            var found=tx.run("MATCH (a:RelationAssertion {uid:$uid}) RETURN a.active AS active",Map.of("uid",uid)).list();if(found.isEmpty())throw missing();
            if(found.getFirst().get("active").asBoolean(false)){
                tx.run("MATCH (a:RelationAssertion {uid:$uid}) SET a.active=false,a.withdrawnAt=$now,a.withdrawnBy=$actor,a.withdrawReason=$reason WITH a MATCH (a)-[:OBSERVED_IN]->(w:Universe) WITH DISTINCT w SET w.evidenceRevision=w.evidenceRevision+1",Map.of("uid",uid,"actor",actor,"now",Instant.now().toString(),"reason",reason)).consume();
            }
            return Map.<String,Object>of("uid",uid,"active",false);
        },TransactionConfig.builder().withTimeout(Duration.ofSeconds(10)).build());}
    }
    public List<Map<String,Object>> list(int limit){try(var s=session()){return s.executeRead(tx->tx.run("MATCH (b:EvidenceImportBatch) RETURN b{.uid,.packageHash,.worldUid,.status,.createdBy,.createdAt,.provider,.sourceVersion,.evidenceCount,.disabledAt,.disabledBy,.disableReason} AS batch ORDER BY b.createdAt DESC,b.uid LIMIT $limit",Map.of("limit",limit)).list(r->r.get("batch").asMap()));}}
    private void lockWorlds(TransactionContext tx,String root,List<String> assertions){
        var worlds=new TreeSet<>(tx.run("MATCH (a:RelationAssertion)-[:OBSERVED_IN]->(w:Universe) WHERE a.uid IN $ids RETURN DISTINCT w.uid AS uid",Map.of("ids",assertions)).list(r->r.get("uid").asString()));worlds.add(root);
        for(String world:worlds)tx.run("MATCH (w:Universe {uid:$uid}) SET w.evidenceRevision=coalesce(w.evidenceRevision,0)",Map.of("uid",world)).consume();
    }
    private void bumpWorlds(TransactionContext tx,List<String> assertions){tx.run("MATCH (a:RelationAssertion)-[:OBSERVED_IN]->(w:Universe) WHERE a.uid IN $ids WITH DISTINCT w SET w.evidenceRevision=coalesce(w.evidenceRevision,0)+1",Map.of("ids",assertions)).consume();}
    private Map<String,Object> batch(TransactionContext tx,String uid){var rows=tx.run("MATCH (b:EvidenceImportBatch {uid:$uid}) RETURN b{.uid,.packageHash,.worldUid,.status,.createdBy,.createdAt,.provider,.sourceVersion,.evidenceCount,.disabledAt,.disabledBy,.disableReason} AS batch",Map.of("uid",uid)).list();return rows.isEmpty()?Map.of():rows.getFirst().get("batch").asMap();}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static ApiException conflict(String message){return new ApiException(HttpStatus.CONFLICT,"EVIDENCE_PREVIEW_CHANGED",message);}
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","关系或证据批次不存在");}
}
