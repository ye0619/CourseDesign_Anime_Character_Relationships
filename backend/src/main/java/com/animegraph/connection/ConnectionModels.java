package com.animegraph.connection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ConnectionModels {
    private ConnectionModels() {}
    public record Endpoint(@NotBlank @Size(max=200) String characterUid, @Min(1) long subjectId) {}
    public record Query(@NotNull @Valid Endpoint from, @NotNull @Valid Endpoint to,
                        @Min(2) @Max(12) Integer maxHops, @Min(1) @Max(50) Integer limit,
                        boolean spoilers, String storyDirection, @Size(max=80) String language, boolean includeUnknownLanguage,
                        @Size(max=120) String version, boolean includeManualRelations, boolean requireStoryRelation, @Size(max=100) String graphRevision,
                        boolean includeEnded) {
        public Query { if(maxHops==null)maxHops=8;if(limit==null)limit=10;if(storyDirection==null)storyDirection="CONNECTIVITY"; }
    }
    public record Node(String uid, String kind, String name, String imageUrl, List<String> names) {}
    public record Evidence(String provider, String externalId, String url, String sourceField, String revision,
                           String retrievedAt, String reviewStatus, String text) {}
    public record Fact(String uid, String kind, String source, String target, long subjectId, String semantic,
                       boolean directed, boolean spoiler, boolean ended, boolean manual, String language,
                       String version, String episodeScope, String scope, List<Evidence> evidence) {}
    public record Subject(long subjectId, String title, String castStatus, String storyStatus, int characters, int voiceFacts, int storyFacts) {}
    public record Projection(String revision, Instant builtAt, List<Node> nodes, List<Fact> facts,
                             List<Endpoint> appearances, List<Subject> subjects, String adapterVersion) {}
    public record Candidate(String characterUid, long subjectId, String name, String imageUrl, String workTitle,
                            List<String> names, boolean hasVoice, String matchedName, Map<String,com.fasterxml.jackson.databind.JsonNode> portraitSettings) {}
    public record PathNode(String uid, String kind, String name, String imageUrl, Long subjectId) {}
    public record Step(Fact fact, String traversalSource, String traversalTarget, boolean reversed) {}
    public record Path(String signature, List<PathNode> nodes, List<Step> edges, int hopCount,
                       int voiceEdgeCount, int storyEdgeCount, int crossWorkCount) {}
    public record Result(String queryId, String status, String shortestPhase, String morePhase, Query query,
                         @Size(max=100) String graphRevision, List<Path> paths, int returnedPaths, long foundPathCount,
                         Long exactPathCount, boolean searchExhausted, boolean shortestProven, boolean truncated,
                         String stopReason, long states, long edgeChecks, long elapsedMillis, List<Subject> scope) {}
    public record BuildRequest(@Size(max=500) List<@Min(1) Long> subjectIds) {}
    public record BuildJob(String id, String status, int processed, int total, String error, String revision) {}
}
