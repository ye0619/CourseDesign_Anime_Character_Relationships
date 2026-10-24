package com.animegraph.catalog;

import com.animegraph.common.*;
import com.animegraph.identity.ReviewedNameProjection;
import com.animegraph.graph.GraphStore;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final GraphStore graph;private final ReviewedNameProjection names;
    public CatalogService(GraphStore graph,ReviewedNameProjection names) {this.graph=graph;this.names=names;}
    public PageResult<Map<String,Object>> list(String type,String keyword,Integer year,String format,Boolean active,int page,int size,String source) {
        var kind=GraphStore.Kind.parse(type);int offset=PageResult.offset(page,size);
        if(keyword!=null && keyword.length()>100) throw GraphStore.bad("搜索词不能超过100字");
        var params=new HashMap<String,Object>();params.put("keyword",keyword==null?"":keyword.toLowerCase(Locale.ROOT));params.put("year",year);params.put("format",format);params.put("active",active);params.put("offset",offset);params.put("size",size);
        params.put("source",source);params.put("reviewedAliasUids",names.search(type,keyword));
        String match="MATCH (n:Entity:"+kind.label+") WHERE ($active IS NULL OR n.active=$active) AND ($keyword='' OR n.uid IN $reviewedAliasUids OR toLower(n.name) CONTAINS $keyword OR toLower(coalesce(n.nameCn,'')) CONTAINS $keyword OR any(alias IN coalesce(n.aliases,[]) WHERE toLower(alias) CONTAINS $keyword)) AND ($year IS NULL OR n.year=$year) AND ($format IS NULL OR $format='' OR n.format=$format) ";
        match+=" AND ($source IS NULL OR $source='' OR n.source=$source) ";
        long total=number(graph.read(match+"RETURN count(n) AS total",params).getFirst(),"total");
        return new PageResult<>(graph.read(match+"RETURN n AS entity ORDER BY coalesce(n.year,0) DESC,n.uid SKIP $offset LIMIT $size",params).stream().map(row -> GraphStore.castMap(row.get("entity"))).toList(),page,size,total);
    }
    public Map<String,Object> detail(String type,String uid) {
        var entity=graph.entity(uid,false);
        if(!GraphStore.Kind.parse(type).name().equals(entity.get("type"))) throw GraphStore.bad("实体类型不匹配");
        return entity;
    }
    public PageResult<Map<String,Object>> related(String type,String uid,String section,int page,int size) {
        detail(type,uid);int offset=PageResult.offset(page,size);
        if(type.equals("PERSON") && section.equals("works")) {
            String match="CALL { MATCH (:Person {uid:$uid,active:true})-[r:PARTICIPATED_IN {active:true}]->(e:Anime {active:true}) RETURN e,r UNION MATCH (:Person {uid:$uid,active:true})-[r:VOICED {active:true,scopeKnown:true}]->(c:Character {active:true})-[:APPEARS_IN {active:true}]->(e:Anime {active:true}) WHERE r.animeUid=e.uid RETURN e,r } ";
            var params=Map.<String,Object>of("uid",uid,"offset",offset,"size",size);
            long total=number(graph.read(match+"RETURN count(DISTINCT e) AS total",params).getFirst(),"total");
            return new PageResult<>(graph.read(match+"WITH e,collect(DISTINCT coalesce(r.roleName,r.roleCode,'配音')) AS roles RETURN e AS entity,roles ORDER BY coalesce(e.year,0) DESC,e.uid SKIP $offset LIMIT $size",params),page,size,total);
        }
        String match=switch(type+"/"+section) {
            case "ANIME/staff" -> "MATCH (n:Anime {uid:$uid})<-[r:PARTICIPATED_IN]-(e:Person)";
            case "ANIME/characters" -> "MATCH (n:Anime {uid:$uid})<-[r:APPEARS_IN]-(e:Character)";
            case "ANIME/cast" -> "MATCH (n:Anime {uid:$uid})<-[:APPEARS_IN {active:true}]-(c:Character)<-[r:VOICED]-(e:Person) WHERE r.scopeKnown=true AND r.animeUid=$uid AND c.active=true";
            case "PERSON/works" -> "MATCH (n:Person {uid:$uid})-[r:PARTICIPATED_IN]->(e:Anime)";
            case "CHARACTER/works" -> "MATCH (n:Character {uid:$uid})-[r:APPEARS_IN]->(e:Anime)";
            case "CHARACTER/cast" -> "MATCH (n:Character {uid:$uid})<-[r:VOICED]-(e:Person)";
            default -> throw GraphStore.bad("不支持的详情分组");
        };
        match+=(match.contains(" WHERE ")?" AND ":" WHERE ")+"n.active=true AND e.active=true AND r.active=true ";
        if(type.equals("CHARACTER") && section.equals("cast")) match+="AND (r.scopeKnown=false OR EXISTS {MATCH (n)-[:APPEARS_IN {active:true}]->(a:Anime {active:true}) WHERE a.uid=r.animeUid}) ";
        var params=Map.<String,Object>of("uid",uid,"offset",offset,"size",size);
        long total=number(graph.read(match+"RETURN count(r) AS total",params).getFirst(),"total");
        String extra=type.equals("ANIME")&&section.equals("cast")?",c AS character":"";
        return new PageResult<>(graph.read(match+"RETURN e AS entity,properties(r) AS relation"+extra+" ORDER BY e.uid,r.uid SKIP $offset LIMIT $size",params),page,size,total);
    }
    public PageResult<Map<String,Object>> relations(String type,String entityUid,Boolean active,int page,int size) {
        if(type!=null && !type.isBlank() && !GraphStore.RELATIONS.contains(type)) throw GraphStore.bad("不支持的关系类型");
        var params=new HashMap<String,Object>();params.put("type",type);params.put("uid",entityUid);params.put("active",active);params.put("offset",PageResult.offset(page,size));params.put("size",size);
        String match="MATCH (s:Entity)-[r]->(t:Entity) WHERE r.uid IS NOT NULL AND ($type IS NULL OR $type='' OR type(r)=$type) AND ($uid IS NULL OR $uid='' OR s.uid=$uid OR t.uid=$uid) AND ($active IS NULL OR r.active=$active) ";
        return new PageResult<>(graph.read(match+"RETURN r.uid AS uid,type(r) AS type,s AS source,t AS target,properties(r) AS properties ORDER BY r.uid SKIP $offset LIMIT $size",params),page,size,number(graph.read(match+"RETURN count(r) AS total",params).getFirst(),"total"));
    }
    public static long number(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
}
