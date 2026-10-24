package com.animegraph.viewer;

import com.animegraph.graph.GraphStore;
import java.util.*;
import org.springframework.stereotype.Service;

/** Reads saved credits for any watched subject; never imports or fetches. */
@Service
public class LocalReportCredits {
    private final GraphStore graph;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public LocalReportCredits(GraphStore graph,com.fasterxml.jackson.databind.ObjectMapper json) {
        this.graph = graph; this.json=json;
    }
    Map<Long,ReportCredits.Work> read(List<Long> ids) {
        var uids = ids.stream().map(id -> "anime:bangumi:" + id).distinct().toList();
        if (uids.isEmpty()) return Map.of();
        var rows = graph.read("""
            UNWIND $uids AS uid
            MATCH (a:Entity:Anime {uid:uid,active:true})
            CALL { WITH a
              MATCH (p:Person {active:true})-[r:PARTICIPATED_IN {active:true}]->(a)
              WITH p,r ORDER BY r.uid LIMIT 5001
              RETURN collect({uid:p.uid,name:coalesce(p.nameCn,p.name),role:coalesce(r.roleName,r.roleCode),
                              recordUid:r.uid,updatedAt:r.updatedAt}) AS staff }
            CALL { WITH a
              MATCH (p:Person {active:true})-[r:VOICED {active:true,scopeKnown:true}]->(c:Character {active:true})
              WHERE r.animeUid=a.uid AND EXISTS { MATCH (c)-[:APPEARS_IN {active:true}]->(a) }
              WITH p,r,c ORDER BY r.uid LIMIT 5001
              RETURN collect({uid:p.uid,name:coalesce(p.nameCn,p.name),role:'配音',
                              recordUid:r.uid,updatedAt:r.updatedAt,personImage:p.imageUrl,
                              characterUid:c.uid,characterName:coalesce(c.nameCn,c.name),characterImage:c.imageUrl}) AS cast }
            RETURN a.uid AS uid,a.updatedAt AS updatedAt,staff,cast
            LIMIT 5000
            """, Map.of("uids", uids));
        var result = new HashMap<Long,ReportCredits.Work>();
        for (var row : rows) {
            long id = Long.parseLong(String.valueOf(row.get("uid")).substring("anime:bangumi:".length()));
            var staff = records(row.get("staff")); var cast = records(row.get("cast"));
            var participation = new ArrayList<ReportCredits.Participation>();
            for (var records : List.of(staff,cast)) for (var item : records.stream().limit(5000).toList()) {
                String role = Objects.toString(item.get("role"), "");
                if (!role.isBlank()) participation.add(new ReportCredits.Participation(
                        String.valueOf(item.get("uid")), String.valueOf(item.get("name")), 1, role));
            }
            // The graph has no trusted source-pagination audit. Presence and recent writes cannot prove completeness/freshness.
            result.put(id, new ReportCredits.Work(id, "NEO4J_LOCAL",
                    version(row),
                    section(staff.size()), section(cast.size()), List.copyOf(participation),
                    cast.stream().filter(item->item.get("characterUid")!=null).map(item->new ReportCredits.Performance(
                        String.valueOf(item.get("uid")),Objects.toString(item.get("name"),"声优"),Objects.toString(item.get("personImage"),""),
                        String.valueOf(item.get("characterUid")),Objects.toString(item.get("characterName"),"未命名角色"),Objects.toString(item.get("characterImage"),""))).distinct().toList(),List.of()));
        }
        return result;
    }
    private ReportCredits.Section section(int records) {
        return new ReportCredits.Section(records > 5000 ? "PARTIAL" : "NOT_VERIFIED", "NOT_VERIFIED", null, Math.min(records,5000));
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> records(Object value) { return (List<Map<String,Object>>) value; }
    private String version(Map<String,Object> row) {
        try{return GraphStore.hash(json.writeValueAsBytes(row));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot version local report input",e);}
    }
}
