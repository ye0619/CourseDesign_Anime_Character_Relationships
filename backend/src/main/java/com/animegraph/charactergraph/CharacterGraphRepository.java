package com.animegraph.charactergraph;

import com.animegraph.charactergraph.CharacterGraphModels.GraphData;
import com.animegraph.charactergraph.CharacterGraphModels.Work;
import java.util.List;
import java.util.Optional;

public interface CharacterGraphRepository {
    List<Work> listWorks(int limit);
    Optional<GraphData> findGraph(String animeUid, int maxNodes, int maxEdges);
}
