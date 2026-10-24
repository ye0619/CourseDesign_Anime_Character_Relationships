package com.animegraph.charactergraph;

import static com.animegraph.charactergraph.CharacterGraphModels.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class Neo4jCharacterGraphRepository implements CharacterGraphRepository {
    private static final String WORK_COUNTS = """
            CALL {
              WITH a
              OPTIONAL MATCH (c:Character {active:true})-[:APPEARS_IN {active:true}]->(a)
              WHERE c.uid IS NOT NULL
              RETURN count(DISTINCT c) AS characterCount
            }
            CALL {
              WITH a
              MATCH (s:Character {active:true})-[r:CHARACTER_RELATION {active:true}]->(t:Character {active:true})
              WHERE r.animeUid = a.uid AND r.uid IS NOT NULL AND r.relationCode IN $allowedTypes
                AND s <> t
                AND EXISTS { MATCH (s)-[:APPEARS_IN {active:true}]->(a) }
                AND EXISTS { MATCH (t)-[:APPEARS_IN {active:true}]->(a) }
              RETURN count(DISTINCT r) AS relationCount
            }
            RETURN a.uid AS uid, a.name AS name, a.nameCn AS nameCn,
                   characterCount, relationCount
            ORDER BY uid
            """;
    private final Driver driver;
    private final String database;
    private final List<String> allowedTypes = Arrays.stream(RelationType.values()).map(Enum::name).toList();

    public Neo4jCharacterGraphRepository(Driver driver, @Value("${app.neo4j.database}") String database) {
        this.driver = driver;
        this.database = database;
    }

    public List<Work> listWorks(int limit) {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(tx -> tx.run("""
                    MATCH (a:Anime {active:true})
                    WHERE a.uid IS NOT NULL
                      AND EXISTS { MATCH (:Character {active:true})-[:APPEARS_IN {active:true}]->(a) }
                    WITH a ORDER BY a.uid LIMIT $limit
                    """ + WORK_COUNTS + " LIMIT $limit",
                    Map.of("allowedTypes", allowedTypes, "limit", limit)).list(this::work), timeout());
        }
    }

    public Optional<GraphData> findGraph(String animeUid, int maxNodes, int maxEdges) {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(tx -> {
                var workRows = tx.run("MATCH (a:Anime {uid:$animeUid, active:true})\n" + WORK_COUNTS,
                        Map.of("animeUid", animeUid, "allowedTypes", allowedTypes)).list(this::work);
                if (workRows.isEmpty()) return Optional.empty();
                var fetchedNodes = tx.run("""
                        MATCH (c:Character {active:true})-[appearance:APPEARS_IN {active:true}]->(:Anime {uid:$animeUid, active:true})
                        WHERE c.uid IS NOT NULL
                        RETURN DISTINCT c.uid AS id, c.name AS name, c.nameCn AS nameCn,
                               c.groupName AS groupName, coalesce(appearance.characterRole,c.role) AS role, c.summary AS description,
                               c.name AS originalName,c.imageUrl AS imageUrl,c.characterType AS characterType,c.nameCnSource AS nameCnSource
                        ORDER BY CASE role WHEN '主角' THEN 0 WHEN '配角' THEN 1 ELSE 2 END,id LIMIT $limit
                        """, Map.of("animeUid", animeUid, "limit", maxNodes + 1)).list(row ->
                        new CharacterNode(row.get("id").asString(), preferred(row, "nameCn", "name", "未命名角色"),
                                row.get("groupName").asString("未分组"), row.get("role").asString("角色"),
                                row.get("description").asString("暂无角色说明"),row.get("originalName").asString(null),
                                row.get("imageUrl").asString(null),row.get("characterType").isNull()?null:row.get("characterType").asInt(),row.get("nameCnSource").asString(null)));
                var nodes = fetchedNodes.stream().limit(maxNodes).toList();
                var ids = nodes.stream().map(CharacterNode::id).toList();
                var fetchedEdges = tx.run("""
                        MATCH (s:Character {active:true})-[r:CHARACTER_RELATION {active:true}]->(t:Character {active:true})
                        WHERE r.animeUid=$animeUid AND r.uid IS NOT NULL
                          AND s.uid IN $nodeUids AND t.uid IN $nodeUids AND s <> t
                          AND r.relationCode IN $allowedTypes
                        RETURN DISTINCT r.uid AS id, s.uid AS source, t.uid AS target,
                               r.relationCode AS relationType, r.label AS label,
                               r.description AS description, r.evidence AS evidence, r.sourceName AS sourceName,
                               r.spoiler AS spoiler,r.ended AS ended,r.sourceUrl AS sourceUrl,r.directed AS directed,
                               r.sourceRelationId AS sourceRelationId,r.archiveRevision AS archiveRevision
                        ORDER BY id LIMIT $limit
                        """, Map.of("animeUid", animeUid, "nodeUids", ids, "allowedTypes", allowedTypes,
                        "limit", maxEdges + 1)).list(row -> {
                    RelationType type = RelationType.valueOf(row.get("relationType").asString());
                    return new CharacterEdge(row.get("id").asString(), row.get("source").asString(),
                            row.get("target").asString(), type, row.get("label").asString(type.label()),
                            row.get("description").asString("暂无关系说明"),
                            row.get("evidence").asString("尚未补充出处"),
                            row.get("sourceName").asString("项目维护数据"), row.get("directed").asBoolean(type.directed()),row.get("spoiler").asBoolean(false),
                            row.get("ended").asBoolean(false),row.get("sourceUrl").asString(null),row.get("sourceRelationId").isNull()?null:row.get("sourceRelationId").asInt(),row.get("archiveRevision").asString(null));
                });
                return Optional.of(new GraphData(workRows.getFirst(), nodes,
                        fetchedEdges.stream().limit(maxEdges).toList(),
                        fetchedNodes.size() > maxNodes || fetchedEdges.size() > maxEdges));
            }, timeout());
        }
    }

    private Work work(Record row) {
        return new Work(row.get("uid").asString(), preferred(row, "nameCn", "name", "未命名作品"),
                "作品内已维护的角色关系", row.get("characterCount").asInt(), row.get("relationCount").asInt());
    }

    private String preferred(Record row, String first, String second, String fallback) {
        String value = row.get(first).asString("");
        return value.isBlank() ? row.get(second).asString(fallback) : value;
    }

    private TransactionConfig timeout() {
        return TransactionConfig.builder().withTimeout(Duration.ofSeconds(3)).build();
    }
}
