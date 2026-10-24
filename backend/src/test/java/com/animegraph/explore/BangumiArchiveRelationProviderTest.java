package com.animegraph.explore;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BangumiArchiveRelationProviderTest {
    @TempDir Path directory;
    final ObjectMapper json=new ObjectMapper();
    void index(Object subject)throws Exception {
        String version="a".repeat(64);Files.writeString(directory.resolve("CURRENT"),version);
        Path folder=Files.createDirectories(directory.resolve(version));json.writeValue(folder.resolve("1.json").toFile(),subject);
    }
    @Test void rawTypesScopeSpoilersAndTeacherDirectionRemainTraceable()throws Exception {
        index(Map.of("1",Map.of("characterIds",List.of(10,20,30),"records",List.of(
                Map.of("person_type","crt","person_id",10,"related_person_id",20,"relation_type",2012,"spoiler",true,"ended",true),
                Map.of("person_type","prsn","person_id",10,"related_person_id",20,"relation_type",2002),
                Map.of("person_type","crt","person_id",10,"related_person_id",30,"relation_type",2002)))));
        var result=new BangumiArchiveRelationProvider(json,directory.toString()).relations(1,Set.of("character:bangumi:10","character:bangumi:20"));
        assertEquals("VERIFIED",result.status());assertEquals(1,result.edges().size());
        var edge=result.edges().getFirst();assertEquals("character:bangumi:20",edge.source());assertEquals("character:bangumi:10",edge.target());
        assertTrue(edge.directed());assertTrue(edge.spoiler());assertTrue(edge.ended());assertTrue(edge.evidence().contains("2012"));
        assertEquals(2012,edge.sourceRelationId());assertEquals("a".repeat(64),edge.archiveRevision());
    }
    @Test void distinguishesNeverImportedMissingSubjectEmptyAndFailedIndex()throws Exception {
        var provider=new BangumiArchiveRelationProvider(json,directory.toString());
        assertEquals("NOT_FETCHED",provider.relations(1,Set.of()).status());
        index(Map.of("1",Map.of("characterIds",List.of(10),"records",List.of())));
        assertEquals("EMPTY",provider.relations(1,Set.of("character:bangumi:10")).status());
        assertEquals("NOT_IN_ARCHIVE",provider.relations(257,Set.of()).status());
        Files.writeString(directory.resolve("CURRENT"),"../../unsafe");assertEquals("FAILED",provider.relations(1,Set.of()).status());
    }
}
