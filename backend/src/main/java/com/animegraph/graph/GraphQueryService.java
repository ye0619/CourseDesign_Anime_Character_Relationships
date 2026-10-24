package com.animegraph.graph;

import com.animegraph.common.PageResult;
import com.animegraph.catalog.CatalogService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GraphQueryService {
    private final GraphStore graph;
    public GraphQueryService(GraphStore graph) {this.graph=graph;}
    private List<String> types(String input) {
        var types=input==null||input.isBlank()?List.of("PARTICIPATED_IN","APPEARS_IN","RELATED_TO"):Arrays.stream(input.split(",")).map(String::trim).distinct().toList();
        if(types.isEmpty() || !GraphStore.RELATIONS.containsAll(types)) throw GraphStore.bad("关系类型无效");return types;
    }
    public Map<String,Object> neighbors(String uid,String relationTypes,int maxNodes,String animeUid) {
        if(maxNodes<2 || maxNodes>200) throw GraphStore.bad("节点上限需为2—200");
        var center=graph.entity(uid,false);var allowed=types(relationTypes);
        if(allowed.contains("VOICED") && (animeUid==null||animeUid.isBlank())) throw GraphStore.bad("配音图谱必须指定作品范围");
        if(animeUid!=null && !animeUid.isBlank()) requireKind(animeUid,"ANIME");
        var params=new HashMap<String,Object>();params.put("uid",uid);params.put("types",allowed);params.put("anime",animeUid);params.put("limit",maxNodes);
        String match="MATCH (c:Entity {uid:$uid,active:true})-[r]-(n:Entity {active:true}) WHERE type(r) IN $types AND r.active=true AND (type(r)<>'VOICED' OR (r.scopeKnown=true AND r.animeUid=$anime)) AND (type(r)<>'CHARACTER_RELATION' OR $anime IS NULL OR r.animeUid=$anime) ";
        var rows=graph.read(match+"RETURN DISTINCT n AS node ORDER BY n.uid LIMIT $limit",params);
        boolean truncated=rows.size()>=maxNodes;
        var nodes=new ArrayList<Map<String,Object>>();nodes.add(center);rows.stream().limit(maxNodes-1).forEach(row -> nodes.add(GraphStore.castMap(row.get("node"))));
        params.put("uids",nodes.stream().map(node -> node.get("uid")).toList());
        var edges=graph.read("MATCH (s:Entity)-[r]->(t:Entity) WHERE s.uid IN $uids AND t.uid IN $uids AND r.active=true AND type(r) IN $types AND (type(r)<>'VOICED' OR (r.scopeKnown=true AND r.animeUid=$anime)) AND (type(r)<>'CHARACTER_RELATION' OR $anime IS NULL OR r.animeUid=$anime) RETURN r.uid AS uid,s.uid AS source,t.uid AS target,type(r) AS type,properties(r) AS properties ORDER BY r.uid LIMIT 401",params);
        return Map.of("nodes",nodes,"edges",edges.stream().limit(400).toList(),"truncated",truncated||edges.size()>400,"maxNodes",maxNodes,"maxEdges",400);
    }
    public Map<String,Object> common(String a,String b) {
        if(a.equals(b)) throw GraphStore.bad("请选择两部不同作品");requireKind(a,"ANIME");requireKind(b,"ANIME");
        var params=Map.<String,Object>of("a",a,"b",b);
        String staff="MATCH (p:Person {active:true})-[ra:PARTICIPATED_IN {active:true}]->(:Anime {uid:$a,active:true}), (p)-[rb:PARTICIPATED_IN {active:true}]->(:Anime {uid:$b,active:true}) ";
        String characters="MATCH (c:Character {active:true})-[:APPEARS_IN {active:true}]->(:Anime {uid:$a,active:true}), (c)-[:APPEARS_IN {active:true}]->(:Anime {uid:$b,active:true}) ";
        String voices="MATCH (p:Person {active:true})-[ra:VOICED {active:true,scopeKnown:true}]->(ca:Character {active:true})-[:APPEARS_IN {active:true}]->(:Anime {uid:$a,active:true}), (p)-[rb:VOICED {active:true,scopeKnown:true}]->(cb:Character {active:true})-[:APPEARS_IN {active:true}]->(:Anime {uid:$b,active:true}) WHERE ra.animeUid=$a AND rb.animeUid=$b ";
        return Map.of("people",group(staff,"p","p AS entity,collect(DISTINCT coalesce(ra.roleName,ra.roleCode)) AS rolesA,collect(DISTINCT coalesce(rb.roleName,rb.roleCode)) AS rolesB",params),"characters",group(characters,"c","DISTINCT c AS entity",params),"voiceActors",group(voices,"p","p AS entity,collect(DISTINCT ca.uid) AS charactersA,collect(DISTINCT cb.uid) AS charactersB",params));
    }
    public Map<String,Object> paths(String from,String to,int depth,int limit) {
        if(depth<1||depth>4||limit<1||limit>20) throw GraphStore.bad("路径深度需为1—4，条数需为1—20");
        graph.entity(from,false);graph.entity(to,false);if(from.equals(to)) throw GraphStore.bad("起点与终点不能相同");
        var rows=graph.read("MATCH p=(s:Entity {uid:$from})-[:PARTICIPATED_IN|APPEARS_IN|RELATED_TO*1.."+depth+"]-(t:Entity {uid:$to}) WHERE all(n IN nodes(p) WHERE n.active=true) AND all(r IN relationships(p) WHERE r.active=true) AND all(n IN nodes(p) WHERE single(m IN nodes(p) WHERE m=n)) RETURN [n IN nodes(p) | n.uid] AS nodeUids,[r IN relationships(p) | {uid:r.uid,type:type(r),source:startNode(r).uid,target:endNode(r).uid}] AS edges,length(p) AS length ORDER BY length(p),nodeUids LIMIT $limit",Map.of("from",from,"to",to,"limit",limit+1));
        var ids=new LinkedHashSet<String>();rows.stream().limit(limit).forEach(row -> ((List<?>)row.get("nodeUids")).forEach(id -> ids.add(id.toString())));
        var nodes=ids.isEmpty()?List.of():graph.read("MATCH (n:Entity) WHERE n.uid IN $ids RETURN n AS node",Map.of("ids",List.copyOf(ids))).stream().map(row -> row.get("node")).toList();
        return Map.of("paths",rows.stream().limit(limit).toList(),"nodes",nodes,"truncated",rows.size()>limit,"maxDepth",depth);
    }
    private Map<String,Object> filters(Integer from,Integer to,String codes) {
        if((from!=null&&(from<1800||from>2200))||(to!=null&&(to<1800||to>2200))||(from!=null&&to!=null&&from>to)) throw GraphStore.bad("年份范围无效");
        var roles=codes==null||codes.isBlank()?List.<String>of():Arrays.stream(codes.split(",")).map(String::trim).distinct().toList();
        if(roles.size()>20||roles.stream().anyMatch(code -> !code.matches("[A-Z0-9_]{1,80}"))) throw GraphStore.bad("岗位筛选值无效");
        var params=new HashMap<String,Object>();params.put("from",from);params.put("to",to);params.put("roles",roles);return params;
    }
    private static final String WORK_FILTER=" AND ($from IS NULL OR a.year >= $from) AND ($to IS NULL OR a.year <= $to) AND (size($roles)=0 OR r.roleCode IN $roles) ";
    public PageResult<Map<String,Object>> ranking(Integer from,Integer to,String roles,int page,int size) {
        var params=filters(from,to,roles);params.put("offset",PageResult.offset(page,size));params.put("size",size);
        String match="MATCH (p:Person {active:true})-[r:PARTICIPATED_IN {active:true}]->(a:Anime {active:true}) WHERE true"+WORK_FILTER;
        return new PageResult<>(graph.read(match+"RETURN p AS entity,count(DISTINCT a) AS workCount ORDER BY workCount DESC,p.uid SKIP $offset LIMIT $size",params),page,size,CatalogService.number(graph.read(match+"RETURN count(DISTINCT p) AS total",params).getFirst(),"total"));
    }
    public PageResult<Map<String,Object>> collaborators(String uid,String other,Integer from,Integer to,String roles,int page,int size) {
        requireKind(uid,"PERSON");if(other!=null){requireKind(other,"PERSON");if(uid.equals(other))throw GraphStore.bad("请选择不同人员");}
        var params=filters(from,to,roles);params.put("uid",uid);params.put("other",other);params.put("offset",PageResult.offset(page,size));params.put("size",size);
        String match="MATCH (p:Person {uid:$uid,active:true})-[r:PARTICIPATED_IN {active:true}]->(a:Anime {active:true})<-[s:PARTICIPATED_IN {active:true}]-(q:Person {active:true}) WHERE q.uid<>$uid AND ($other IS NULL OR q.uid=$other) "+WORK_FILTER+" AND (size($roles)=0 OR s.roleCode IN $roles) ";
        String returned=other==null?"q AS entity,count(DISTINCT a) AS workCount":"a AS entity,collect(DISTINCT coalesce(r.roleName,r.roleCode)) AS rolesA,collect(DISTINCT coalesce(s.roleName,s.roleCode)) AS rolesB";
        String order=other==null?"workCount DESC,q.uid":"a.year DESC,a.uid";
        long total=CatalogService.number(graph.read(match+"RETURN count(DISTINCT "+(other==null?"q":"a")+") AS total",params).getFirst(),"total");
        return new PageResult<>(graph.read(match+"RETURN "+returned+" ORDER BY "+order+" SKIP $offset LIMIT $size",params),page,size,total);
    }
    public Object network(String uid,Integer from,Integer to,String roles,int limit) {
        if(limit<1||limit>50)throw GraphStore.bad("合作网络人数需为1—50");return Map.of("center",graph.entity(uid,false),"collaborators",collaborators(uid,null,from,to,roles,1,limit));
    }
    private void requireKind(String uid,String kind) {if(!kind.equals(graph.entity(uid,false).get("type")))throw GraphStore.bad("实体类型不匹配");}
    private Map<String,Object> group(String match,String alias,String returned,Map<String,Object> params) {
        long total=CatalogService.number(graph.read(match+"RETURN count(DISTINCT "+alias+") AS total",params).getFirst(),"total");
        var items=graph.read(match+"RETURN "+returned+" ORDER BY "+alias+".uid LIMIT 200",params);
        return Map.of("items",items,"returned",items.size(),"truncated",total>items.size(),"total",total);
    }
}
