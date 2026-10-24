package com.animegraph.graph;

import com.animegraph.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GraphStore {
    public enum Kind {
        ANIME("Anime"), PERSON("Person"), CHARACTER("Character");
        public final String label;
        Kind(String label) { this.label=label; }
        public static Kind parse(String value) {
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch(Exception exception) { throw bad("实体类型必须为Anime、Person或Character"); }
        }
    }
    public static final Set<String> RELATIONS=Set.of("PARTICIPATED_IN","APPEARS_IN","VOICED","RELATED_TO","CHARACTER_RELATION");
    private static final Set<String> ENTITY_FIELDS=Set.of("name","nameCn","nameCnSource","characterType","aliases","summary","airDate","year","format","imageUrl","groupName","role","sourceUpdatedAt");
    private static final Set<String> RELATION_FIELDS=Set.of("roleCode","roleName","characterRole","animeUid","language","versionLabel","scopeKnown","relationCode","relationName","label","description","evidence","sourceName","sourceUrl","sourceRelationId","spoiler","ended","directed","archiveRevision");
    private static final Set<String> IDENTITY_FIELDS=Set.of("roleCode","animeUid","language","versionLabel","scopeKnown","relationCode");
    private final Driver driver;
    private final String database;
    private final ObjectMapper json;
    public GraphStore(Driver driver,@Value("${app.neo4j.database}") String database,ObjectMapper json) {
        this.driver=driver;this.database=database;this.json=json;
    }
    /** Backup and graph removal commit together; independent local graph workspaces are untouched. */
    public synchronized Map<String,Object> deleteLocalWork(String uid,Map<String,Object> archive) {
        try(var session=driver.session(SessionConfig.forDatabase(database))) {
            return session.executeWrite(tx -> {
                var params=Map.<String,Object>of("uid",uid);
                var nodes=tx.run("MATCH (n:Entity:Anime {uid:$uid}) RETURN properties(n) AS properties,labels(n) AS labels",params).list(row -> row.asMap());
                if(nodes.isEmpty() && archive.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","本地作品不存在或已经删除");
                var edges=tx.run("MATCH (s)-[r]->(t) WHERE (s:Entity:Anime AND s.uid=$uid) OR (t:Entity:Anime AND t.uid=$uid) OR (s:Entity AND t:Entity AND r.animeUid=$uid) RETURN s.uid AS source,t.uid AS target,type(r) AS type,properties(r) AS properties",params).list(row -> row.asMap());
                String backupId=UUID.randomUUID().toString();
                String payload=toJson(Map.of("workUid",uid,"nodes",nodes,"relationships",edges,"savedArchive",archive));
                tx.run("CREATE (:LocalWorkDeletionBackup {uid:$backupId,workUid:$uid,createdAt:$now,payload:$payload})",
                    Map.of("backupId",backupId,"uid",uid,"now",Instant.now().toString(),"payload",payload)).consume();
                tx.run("MATCH (s:Entity)-[r]->(t:Entity) WHERE r.animeUid=$uid DELETE r",params).consume();
                tx.run("MATCH (n:Entity:Anime {uid:$uid}) DETACH DELETE n",params).consume();
                return Map.<String,Object>of("uid",uid,"deleted",true,"backupId",backupId,"deletedRelationships",edges.size());
            },TransactionConfig.builder().withTimeout(Duration.ofSeconds(15)).build());
        }
    }
    public void validateSubjects(List<Long> subjects) {
        if(subjects.stream().anyMatch(id -> id == null || id < 1 || id > Integer.MAX_VALUE)) throw bad("作品编号无效");
    }
    private void requireWriteScope(String uid) {
        if(!uid.matches("(?:anime|person|character):[a-z][a-z0-9_-]{0,29}:[A-Za-z0-9_.-]{1,120}")) throw bad("实体编号无效");
    }
    public void validatePackageScope(String source,JsonNode root) {
        // Reject known out-of-scope works before even shared entity updates begin.
        for(var entity:root.path("entities")) {
            if(entity.path("type").asText().equalsIgnoreCase("ANIME") && entity.path("sourceId").isTextual())
                requireWriteScope(uid(Kind.ANIME,source,entity.path("sourceId").asText()));
        }
        for(var relation:root.path("relations")) {
            for(String side:List.of("from","to")) {
                if(relation.path(side+"Uid").isTextual()) requireWriteScope(relation.path(side+"Uid").asText());
                else if(relation.path(side).path("type").asText().equalsIgnoreCase("ANIME") && relation.path(side).path("sourceId").isTextual())
                    requireWriteScope(uid(Kind.ANIME,source,relation.path(side).path("sourceId").asText()));
            }
            if(relation.path("properties").path("animeUid").isTextual()) requireWriteScope(relation.path("properties").path("animeUid").asText());
        }
    }
    public List<Map<String,Object>> read(String cypher,Map<String,Object> params) {
        try(var session=driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(tx -> tx.run(cypher,params).list(row -> row.asMap(value -> {
                Object object=value.asObject();
                return object instanceof Node node ? entityMap(node) : object;
            })),TransactionConfig.builder().withTimeout(Duration.ofSeconds(3)).build());
        }
    }
    public synchronized void write(String cypher,Map<String,Object> params) {
        try(var session=driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {tx.run(cypher,params).consume();return null;},TransactionConfig.builder().withTimeout(Duration.ofSeconds(10)).build());
        }
    }
    public Map<String,Object> entity(String uid,boolean includeInactive) {
        var rows=read("MATCH (n:Entity {uid:$uid}) WHERE $inactive OR n.active=true RETURN n AS entity",Map.of("uid",uid,"inactive",includeInactive));
        if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","实体不存在或已停用");
        return castMap(rows.getFirst().get("entity"));
    }
    public synchronized String upsertEntity(String source,JsonNode input,String taskId) {
        Kind kind=Kind.parse(required(input,"type"));
        String sourceId=required(input,"sourceId");
        String uid=uid(kind,source,sourceId);
        if(kind==Kind.ANIME) requireWriteScope(uid);
        var properties=new LinkedHashMap<String,Object>();
        input.fields().forEachRemaining(entry -> {
            if(ENTITY_FIELDS.contains(entry.getKey())) properties.put(entry.getKey(),property(entry.getKey(),entry.getValue()));
            else if(!Set.of("type","sourceId").contains(entry.getKey())) throw bad("不支持实体字段："+entry.getKey());
        });
        if(!(properties.get("name") instanceof String name) || name.isBlank()) throw bad("实体名称不能为空");
        try(var session=driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                var existing=tx.run("MATCH (n:Entity {uid:$uid}) RETURN n",Map.of("uid",uid)).list();
                if(!existing.isEmpty()) {
                    Node node=existing.getFirst().get("n").asNode();
                    if(!node.hasLabel(kind.label)) throw bad("实体身份与类型冲突");
                    node.get("manualFields").asList(value -> value.asString(),List.of()).forEach(properties::remove);
                }
                tx.run("MERGE (n:Entity:"+kind.label+" {uid:$uid}) ON CREATE SET n.createdAt=$now,n.active=true,n.source=$source,n.sourceId=$sourceId,n.manualFields=[] SET n += $properties,n.updatedAt=$now,n.lastImportTaskId=$task",
                        Map.of("uid",uid,"source",source,"sourceId",sourceId,"properties",properties,"now",Instant.now().toString(),"task",taskId)).consume();
                return null;
            });
        }
        return uid;
    }
    public synchronized String upsertRelation(String source,JsonNode input,String taskId) {
        String type=required(input,"type");
        if(!RELATIONS.contains(type)) throw bad("不支持的关系类型");
        String from=reference(input,"from",source),to=reference(input,"to",source);
        requireWriteScope(from);requireWriteScope(to);
        if(input.path("properties").hasNonNull("animeUid")) requireWriteScope(input.path("properties").path("animeUid").asText());
        var start=entity(from,false);var end=entity(to,false);
        String combination=start.get("type")+"-"+end.get("type");
        String expected=switch(type) {case "PARTICIPATED_IN" -> "PERSON-ANIME";case "APPEARS_IN" -> "CHARACTER-ANIME";case "VOICED" -> "PERSON-CHARACTER";case "RELATED_TO" -> "ANIME-ANIME";default -> "CHARACTER-CHARACTER";};
        if(!combination.equals(expected) || from.equals(to)) throw bad("关系端点类型不正确或角色相同");
        var properties=new LinkedHashMap<String,Object>();
        var raw=input.path("properties");
        if(!raw.isObject()) throw bad("关系properties必须为对象");
        raw.fields().forEachRemaining(entry -> {
            if(!RELATION_FIELDS.contains(entry.getKey())) throw bad("不支持关系字段："+entry.getKey());
            properties.put(entry.getKey(),property(entry.getKey(),entry.getValue()));
        });
        if(type.equals("PARTICIPATED_IN")) nonblank(properties,"roleCode");
        if(type.equals("RELATED_TO")) nonblank(properties,"relationCode");
        if(type.equals("VOICED")) {
            boolean known=Boolean.TRUE.equals(properties.getOrDefault("scopeKnown",properties.get("animeUid")!=null));
            properties.put("scopeKnown",known);
            if(!known && properties.get("animeUid")!=null) throw bad("未知配音范围不能填写作品UID");
            if(known) {nonblank(properties,"animeUid");checkAppearance(to,String.valueOf(properties.get("animeUid")));}
        }
        if(type.equals("CHARACTER_RELATION")) {
            nonblank(properties,"animeUid");nonblank(properties,"relationCode");nonblank(properties,"evidence");
            String code=String.valueOf(properties.get("relationCode"));
            var definition=com.animegraph.charactergraph.RelationType.parse(code).orElseThrow(() -> bad("无效的角色剧情关系类型"));
            checkAppearance(from,String.valueOf(properties.get("animeUid")));checkAppearance(to,String.valueOf(properties.get("animeUid")));
            if(!definition.directed() && from.compareTo(to)>0) {String temp=from;from=to;to=temp;}
        }
        List<Object> identity=new ArrayList<>(List.of(source,type,from,to));
        switch(type) {
            case "PARTICIPATED_IN" -> identity.add(properties.get("roleCode"));
            case "RELATED_TO" -> identity.add(properties.get("relationCode"));
            case "VOICED" -> {identity.add(properties.getOrDefault("animeUid",""));identity.add(properties.getOrDefault("language",""));identity.add(properties.getOrDefault("versionLabel",""));}
            case "CHARACTER_RELATION" -> {identity.add(properties.get("animeUid"));identity.add(properties.get("relationCode"));}
            default -> { }
        }
        if(input.hasNonNull("sourceId")) uid(Kind.ANIME,source,required(input,"sourceId"));
        String id=input.hasNonNull("sourceId") ? "relation:"+source+":"+type+":"+input.get("sourceId").asText() : "relation:"+hash(toJson(identity).getBytes(StandardCharsets.UTF_8));
        var old=read("MATCH (s)-[r {uid:$uid}]->(t) RETURN s.uid AS source,t.uid AS target,type(r) AS type,r.manualFields AS manual,properties(r) AS properties",Map.of("uid",id));
        if(!old.isEmpty()) {
            var row=old.getFirst();
            if(!from.equals(row.get("source")) || !to.equals(row.get("target")) || !type.equals(row.get("type"))) throw bad("来源关系ID的身份发生变化");
            var previous=castMap(row.get("properties"));
            for(String field:IDENTITY_FIELDS) if(!Objects.equals(previous.get(field),properties.get(field))) throw bad("来源关系ID的身份字段发生变化："+field);
            if(row.get("manual") instanceof List<?> fields) fields.forEach(field -> properties.remove(String.valueOf(field)));
        }
        write("MATCH (s:Entity {uid:$from}),(t:Entity {uid:$to}) MERGE (s)-[r:"+type+" {uid:$uid}]->(t) ON CREATE SET r.active=true,r.manualFields=[],r.source=$source SET r += $properties,r.updatedAt=$now,r.lastImportTaskId=$task",
                Map.of("from",from,"to",to,"uid",id,"source",source,"properties",properties,"now",Instant.now().toString(),"task",taskId));
        return id;
    }
    public synchronized Map<String,Object> patchEntity(String uid,Map<String,Object> changes) {
        if(Boolean.TRUE.equals(changes.get("active"))) requireWriteScope(uid);
        var old=entity(uid,true);var manual=new HashSet<String>();
        if(old.get("manualFields") instanceof List<?> fields) fields.forEach(field -> manual.add(String.valueOf(field)));
        var properties=new LinkedHashMap<String,Object>();
        changes.forEach((key,value) -> {
            if(!ENTITY_FIELDS.contains(key) && !key.equals("active")) throw bad("禁止修改身份或未知字段："+key);
            properties.put(key,key.equals("active")?booleanValue(value):property(key,json.valueToTree(value)));manual.add(key);
        });
        if(properties.containsKey("name") && (properties.get("name")==null || properties.get("name").toString().isBlank())) throw bad("名称不能为空");
        write("MATCH (n:Entity {uid:$uid}) SET n += $properties,n.manualFields=$manual,n.updatedAt=$now",Map.of("uid",uid,"properties",properties,"manual",manual.stream().sorted().toList(),"now",Instant.now().toString()));
        return entity(uid,true);
    }
    public synchronized void patchRelation(String uid,Map<String,Object> changes) {
        var rows=read("MATCH (s)-[r {uid:$uid}]->(t) RETURN properties(r) AS properties,s.uid AS source,t.uid AS target,type(r) AS type",Map.of("uid",uid));
        if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","关系不存在");
        var old=castMap(rows.getFirst().get("properties"));
        var properties=new LinkedHashMap<String,Object>();var manual=new HashSet<String>();
        if(old.get("manualFields") instanceof List<?> fields) fields.forEach(field -> manual.add(String.valueOf(field)));
        changes.forEach((key,value) -> {
            if(key.equals("active")) properties.put(key,booleanValue(value));
            else if(key.equals("properties") && value instanceof Map<?,?> fields) fields.forEach((field,item) -> {
                String name=String.valueOf(field);
                if(!RELATION_FIELDS.contains(name) || IDENTITY_FIELDS.contains(name)) throw bad("身份字段修改需停用旧关系后重建");
                properties.put(name,property(name,json.valueToTree(item)));
            });
            else throw bad("不支持的关系修改字段");
        });
        var merged=new HashMap<>(old);merged.putAll(properties);
        String type=String.valueOf(rows.getFirst().get("type"));
        if(type.equals("CHARACTER_RELATION")) nonblank(merged,"evidence");
        if(Boolean.TRUE.equals(merged.get("active"))) {
            String from=String.valueOf(rows.getFirst().get("source")),to=String.valueOf(rows.getFirst().get("target"));
            requireWriteScope(from);requireWriteScope(to);
            if(merged.get("animeUid")!=null) requireWriteScope(String.valueOf(merged.get("animeUid")));
            entity(from,false);entity(to,false);
            if(type.equals("VOICED") && Boolean.TRUE.equals(merged.get("scopeKnown"))) checkAppearance(to,String.valueOf(merged.get("animeUid")));
            if(type.equals("CHARACTER_RELATION")) {checkAppearance(from,String.valueOf(merged.get("animeUid")));checkAppearance(to,String.valueOf(merged.get("animeUid")));}
        }
        manual.addAll(properties.keySet());
        write("MATCH ()-[r {uid:$uid}]->() SET r += $properties,r.manualFields=$manual,r.updatedAt=$now",Map.of("uid",uid,"properties",properties,"manual",manual.stream().sorted().toList(),"now",Instant.now().toString()));
    }
    private void checkAppearance(String character,String anime) {
        if(read("MATCH (:Character {uid:$character,active:true})-[:APPEARS_IN {active:true}]->(:Anime {uid:$anime,active:true}) RETURN 1 AS found LIMIT 1",Map.of("character",character,"anime",anime)).isEmpty()) throw bad("角色未在指定有效作品中出场");
    }
    private Object property(String key,JsonNode value) {
        if(value==null || value.isNull()) return null;
        if(key.equals("aliases")) {
            if(!value.isArray() || value.size()>100) throw bad("别名必须是最多100项的字符串数组");
            List<String> aliases=new ArrayList<>();value.forEach(alias -> {if(!alias.isTextual() || alias.asText().length()>300) throw bad("别名格式不正确");aliases.add(alias.asText());});return aliases;
        }
        if(key.equals("year")) {if(!value.isIntegralNumber() || value.asInt()<1800 || value.asInt()>2200) throw bad("年份无效");return value.asInt();}
        if(Set.of("scopeKnown","spoiler","ended","directed").contains(key)) {if(!value.isBoolean()) throw bad(key+"必须为布尔值");return value.asBoolean();}
        if(key.equals("characterType")) {if(!value.isIntegralNumber() || value.asInt()<1 || value.asInt()>4) throw bad("角色实体类型需为1—4");return value.asInt();}
        if(key.equals("sourceRelationId")) {if(!value.isIntegralNumber()) throw bad("来源关系类型编号必须为整数");return value.asInt();}
        if(!value.isTextual() || value.asText().length()>(Set.of("summary","description").contains(key)?20000:1000)) throw bad("字段格式或长度不正确："+key);
        String text=value.asText();
        if(key.equals("airDate") && !text.isBlank()) try{LocalDate.parse(text);}catch(Exception e){throw bad("日期必须为YYYY-MM-DD");}
        if(Set.of("imageUrl","nameCnSource","sourceUrl").contains(key) && !text.isBlank() && !text.matches("https?://[^\\s]+")) throw bad("来源及图片地址必须为HTTP(S)");
        return text;
    }
    private boolean booleanValue(Object value) {if(!(value instanceof Boolean)) throw bad("active必须为布尔值");return (Boolean)value;}
    private String reference(JsonNode input,String name,String source) {
        if(input.hasNonNull(name+"Uid")) return input.get(name+"Uid").asText();
        JsonNode node=input.path(name);return uid(Kind.parse(required(node,"type")),source,required(node,"sourceId"));
    }
    public static String uid(Kind kind,String source,String sourceId) {
        if(!source.matches("[a-z][a-z0-9_-]{0,29}") || !sourceId.matches("[A-Za-z0-9_.-]{1,120}")) throw bad("来源或来源ID格式不正确");
        return kind.name().toLowerCase(Locale.ROOT)+":"+source+":"+sourceId;
    }
    public static String required(JsonNode input,String name) {if(!input.hasNonNull(name) || !input.get(name).isTextual() || input.get(name).asText().isBlank()) throw bad("缺少文本字段："+name);return input.get(name).asText();}
    private static void nonblank(Map<String,Object> map,String name) {if(!(map.get(name) instanceof String value) || value.isBlank()) throw bad("缺少关系字段："+name);}
    private Map<String,Object> entityMap(Node node) {var map=new LinkedHashMap<>(node.asMap());for(Kind kind:Kind.values()) if(node.hasLabel(kind.label)) map.put("type",kind.name());return map;}
    @SuppressWarnings("unchecked") public static Map<String,Object> castMap(Object object) {return (Map<String,Object>)object;}
    public String toJson(Object object) {try{return json.writeValueAsString(object);}catch(Exception e){throw bad("JSON序列化失败");}}
    public static String hash(byte[] data) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception e){throw new IllegalStateException(e);}}
    public static ApiException bad(String message) {return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message);}
}
