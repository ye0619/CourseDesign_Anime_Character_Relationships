package com.animegraph.explore;

import com.animegraph.charactergraph.CharacterGraphModels.CharacterEdge;
import java.util.List;
import java.util.Set;

/** Provider boundary uses stable source-scoped IDs, never display names. */
public interface CharacterRelationProvider {
    record Result(String source,String status,String revision,String notice,List<CharacterEdge> edges) {}
    Result relations(long subjectId,Set<String> characterUids);
}
