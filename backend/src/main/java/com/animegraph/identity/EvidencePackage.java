package com.animegraph.identity;

import com.animegraph.charactergraph.RelationType;
import com.animegraph.graph.GraphStore;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** Reviewed evidence only. Never matches names, creates characters, or fetches supplied URLs. */
public record EvidencePackage(String batchId,String worldUid,String provider,String sourceVersion,
                              String licenseLabel,String retrievedAt,List<Fact> facts) {
    public record Fact(String uid,String evidenceUid,String source,String target,String relationType,String label,
                       boolean directed,boolean ended,boolean spoiler,String scopeType,List<Long> subjectIds,
                       String sourceUrl,String sourceRevision,String sourceField,String rawRelation,
                       String sourceName,String description,String attribution,String matchMethod) {}
    private static final Set<String> ROOT=Set.of("schemaVersion","batchId","worldUid","provider","sourceVersion","licenseLabel","retrievedAt","facts");
    private static final Set<String> FIELDS=Set.of("source","target","relationType","ended","spoiler","scopeType","subjectIds","sourceUrl","sourceRevision","sourceField","rawRelation","sourceName","description","attribution","matchMethod","reviewStatus");
    public static EvidencePackage parse(JsonNode root,ObjectMapper json) {
        fields(root,ROOT);if(!root.path("schemaVersion").isIntegralNumber()||!root.path("schemaVersion").canConvertToInt()||root.path("schemaVersion").asInt()!=1)throw GraphStore.bad("证据包schemaVersion必须为1");
        var batch=text(root,"batchId",64);if(!batch.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))throw GraphStore.bad("批次编号格式不正确");
        var world=text(root,"worldUid",120);if(!world.matches("universe:[a-z0-9_-]+:[A-Za-z0-9._-]+"))throw GraphStore.bad("世界观编号不正确");
        var provider=text(root,"provider",20);if(!Set.of("BANGUMI","MOEGIRL","ACDB","PROJECT").contains(provider))throw GraphStore.bad("来源必须是已支持的证据来源");
        var version=text(root,"sourceVersion",100);var license=text(root,"licenseLabel",200);var retrieved=text(root,"retrievedAt",50);
        try{if(Instant.parse(retrieved).isAfter(Instant.now().plusSeconds(300)))throw new IllegalArgumentException();}catch(Exception e){throw GraphStore.bad("获取时间须为有效UTC时间，不能晚于当前时间");}
        var input=root.path("facts");if(!input.isArray()||input.isEmpty()||input.size()>200)throw GraphStore.bad("每包需包含1—200条已审核事实");
        var facts=new LinkedHashMap<String,Fact>();
        for(var row:input){
            fields(row,FIELDS);if(!"REVIEWED".equals(text(row,"reviewStatus",20)))throw GraphStore.bad("候选事实必须人工审核后才能导入，不自动按同名合并");
            var from=character(row,"source");var to=character(row,"target");if(from.equals(to))throw GraphStore.bad("关系两端不能相同");
            var type=RelationType.parse(text(row,"relationType",30)).orElseThrow(()->GraphStore.bad("关系类型不在共享字典内"));
            if(!type.directed()&&from.compareTo(to)>0){var swap=from;from=to;to=swap;}
            var scope=text(row,"scopeType",30);if(!Set.of("GLOBAL","SUBJECT_EXPLICIT").contains(scope))throw GraphStore.bad("仅支持全局或有明确作品证据的范围");
            var subjects=new TreeSet<Long>();var ids=row.path("subjectIds");if(!ids.isArray()||ids.size()>10)throw GraphStore.bad("subjectIds须为最多10项的数组");
            for(var id:ids){if(!id.isIntegralNumber()||!id.canConvertToLong()||id.asLong()<1||id.asLong()>Integer.MAX_VALUE)throw GraphStore.bad("作品编号不正确");subjects.add(id.asLong());}
            if(scope.equals("GLOBAL")&&!subjects.isEmpty()||scope.equals("SUBJECT_EXPLICIT")&&subjects.isEmpty())throw GraphStore.bad("全局关系不填作品，明确作品关系至少填写一部作品");
            var method=text(row,"matchMethod",30);if(!Set.of("MANUAL","SOURCE_ID").contains(method))throw GraphStore.bad("仅接受人工或稳定来源编号核验，不接受名称推测");
            boolean ended=bool(row,"ended"),spoiler=bool(row,"spoiler");
            String url=url(text(row,"sourceUrl",1000),provider),revision=text(row,"sourceRevision",200),field=text(row,"sourceField",200),raw=text(row,"rawRelation",2000),name=text(row,"sourceName",200),description=text(row,"description",2000),attribution=text(row,"attribution",500);
            var key=new ArrayList<Object>(List.of(from,to,type.name(),type.directed(),ended,scope));
            // Preserve the existing GLOBAL identity. Explicit scopes remain separate facts.
            if(!scope.equals("GLOBAL")){key.add(world);key.add(List.copyOf(subjects));}
            String uid="assertion:"+hash(json,key);
            String evidence="evidence:reviewed:"+hash(json,List.of(uid,provider,url,revision,field,raw,name,description,attribution,method,license));
            var fact=new Fact(uid,evidence,from,to,type.name(),type.label(),type.directed(),ended,spoiler,scope,List.copyOf(subjects),url,revision,field,raw,name,description,attribution,method);
            var previous=facts.putIfAbsent(evidence,fact);if(previous!=null&&!previous.equals(fact))throw GraphStore.bad("同一证据存在互相矛盾的属性，请拆分核验");
        }
        return new EvidencePackage(batch,world,provider,version,license,retrieved,facts.values().stream().sorted(Comparator.comparing(Fact::evidenceUid)).toList());
    }
    public String hash(ObjectMapper json){return hash(json,this);}
    public String batchUid(){return "evidence-batch:"+batchId;}
    static String hash(ObjectMapper json,Object object){try{return GraphStore.hash(json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(object));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String character(JsonNode n,String field){var value=text(n,field,160);if(!value.matches("character:[a-z0-9_-]+:[A-Za-z0-9._-]+"))throw GraphStore.bad("关系端点须为稳定角色编号");return value;}
    private static boolean bool(JsonNode n,String field){if(!n.path(field).isBoolean())throw GraphStore.bad(field+"必须为布尔值");return n.path(field).asBoolean();}
    private static void fields(JsonNode n,Set<String> allowed){if(n==null||!n.isObject())throw GraphStore.bad("证据包和事实须为JSON对象");n.fieldNames().forEachRemaining(f->{if(!allowed.contains(f))throw GraphStore.bad("不支持证据字段："+f);});}
    private static String text(JsonNode n,String field,int max){if(!n.path(field).isTextual())throw GraphStore.bad(field+"须为文本");var value=n.path(field).asText().trim();if(value.isEmpty()||value.length()>max)throw GraphStore.bad(field+"不能为空且不得超长");return value;}
    private static String url(String text,String provider){try{var uri=URI.create(text);var host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT);boolean allowed=switch(provider){case "BANGUMI"->Set.of("bgm.tv","bangumi.tv","chii.in").contains(host);case "MOEGIRL"->host.equals("zh.moegirl.org.cn");case "ACDB"->Set.of("www.animecharactersdatabase.com","animecharactersdatabase.com").contains(host);default->!host.isEmpty();};if(!"https".equals(uri.getScheme())||uri.getRawUserInfo()!=null||uri.getPort()!=-1||!allowed)throw new IllegalArgumentException();return text;}catch(Exception e){throw GraphStore.bad("来源须为该站HTTPS地址，不能包含账号或自定义端口");}}
}
