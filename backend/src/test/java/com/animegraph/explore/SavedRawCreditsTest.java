package com.animegraph.explore;

import com.animegraph.identity.NameResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SavedRawCreditsTest {
    @TempDir Path root;
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @Test void offlinePublicSourceRequiresProvenanceAndKeepsTypesAndUnknownLanguage() throws Exception {
        Files.writeString(root.resolve("v0-subjects-1.json"),"{\"id\":1,\"type\":2,\"name\":\"动画\"}");
        Files.writeString(root.resolve("v0-subjects-1-characters.json"),"[{\"id\":10,\"type\":1,\"name\":\"角色\",\"actors\":[{\"id\":20,\"type\":1,\"name\":\"演员\",\"career\":[\"seiyu\"]},{\"id\":21,\"type\":2,\"name\":\"公司\"}]}]");
        var provider=new SavedRawCredits(json,new NameResolutionService(json),root.toString());assertNull(provider.read(1,"CAST"));
        Files.writeString(root.resolve("provenance.json"),"{\"requests\":[{\"url\":\"https://api.bgm.tv/v0/subjects/1/characters\",\"status\":200,\"fetchedAt\":\"2026-10-23T00:00:00Z\"}]}");
        var page=provider.read(1,"CAST");assertEquals(1,page.cast().size());assertNull(page.cast().getFirst().language());assertEquals(1,page.coverage().omittedCredits());assertEquals("PARTIAL",page.coverage().completeness());assertEquals(64,page.snapshotVersion().length());assertEquals(java.util.List.of(1L),provider.availableSubjects());
    }
    @Test void nonAnimationAndInvalidSourcePathsDoNotEnterCoverage()throws Exception {
        Files.writeString(root.resolve("v0-subjects-2.json"),"{\"id\":2,\"type\":1,\"name\":\"书籍\"}");
        Files.writeString(root.resolve("v0-subjects-2-characters.json"),"[]");
        Files.writeString(root.resolve("provenance.json"),"{\"requests\":[{\"url\":\"https://api.bgm.tv/v0/subjects/2/characters\",\"status\":200,\"fetchedAt\":\"2026-10-23T00:00:00Z\"},{\"url\":\"https://evil.example/v0/subjects/3/characters\",\"status\":200}]}");
        var provider=new SavedRawCredits(json,new NameResolutionService(json),root.toString());assertNull(provider.read(2,"CAST"));assertFalse(provider.availableSubjects().contains(3L));
    }
}
