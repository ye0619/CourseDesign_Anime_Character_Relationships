package com.animegraph.connection;

import java.util.*;
import static com.animegraph.connection.ConnectionModels.*;

public interface CharacterStoryRelationProvider {
    record Stories(String status, List<Fact> facts) {}
    Stories read(long subjectId, Set<String> characterUids);
}
