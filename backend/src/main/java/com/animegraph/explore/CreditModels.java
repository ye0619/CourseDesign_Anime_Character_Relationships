package com.animegraph.explore;

import com.animegraph.identity.NameResolutionService.NameAssertion;
import java.time.Instant;
import java.util.List;

public final class CreditModels {
    private CreditModels() {}
    public record Entity(String id, String label, String originalName, String imageUrl, String category, Integer providerType,
                         List<String> aliases, List<NameAssertion> nameAssertions, String namePolicyVersion, String sourceUrl) {}
    public record Position(String code, String label, String episodeScope, String description) {}
    public record StaffCredit(String personUid, List<Position> positions) {}
    public record CastCredit(String id, String subjectUid, String personUid, String characterUid, Integer sourceRelation,
                             String sourceRelationLabel, String performanceType, String language, String languageSource,
                             String version, String episodeScope, String description, String sourceField) {}
    public record Coverage(int page, int pageSize, int total, int returnedRows, int parsedRows, int omittedRows,
                           int omittedCredits, int unknownCastRows, Integer nextPage, boolean exhausted, boolean capped, String completeness) {}
    public record Source(String provider, String sourceUrl, String sourceField, String adapterVersion, Instant fetchedAt,
                         boolean cached, boolean stale, String namePolicyVersion) {}
    public record Page(String subjectUid, String kind, List<Entity> people, List<Entity> characters, List<StaffCredit> staff,
                       List<CastCredit> cast, Coverage coverage, Source source, String snapshotVersion) {}
}
