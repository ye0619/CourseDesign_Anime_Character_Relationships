package com.animegraph.connection;

import com.animegraph.explore.CreditModels;
import java.util.*;

/** Acquisition is explicit and separate from search. Implementations must never fetch on search. */
public interface VoiceCreditProvider {
    record Work(long subjectId, String title, List<CreditModels.Page> pages) {}
    default List<ConnectionModels.Node> localCharacters(long subjectId){return List.of();}
    List<Long> availableSubjects();
    List<Work> read(List<Long> ids);
}
