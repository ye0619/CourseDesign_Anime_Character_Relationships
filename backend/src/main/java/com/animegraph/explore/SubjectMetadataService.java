package com.animegraph.explore;

import static com.animegraph.explore.CreditModels.*;
import com.animegraph.common.ApiException;
import com.animegraph.identity.EntityIdentity;
import com.animegraph.identity.NameResolutionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** One explicitly requested public page, independent of canvas limits and viewer state. */
@Service
public class SubjectMetadataService {
    public static final int PAGE_SIZE = 60, MAX_PAGE = 100, MAX_CAST_CREDITS = 2000;
    public static final String ADAPTER_VERSION = "bangumi-p1-credits/1.1";
    private final NetworkSourceCache sources;
    private final BangumiAnimeService anime;
    private final NameResolutionService names;

    public SubjectMetadataService(NetworkSourceCache sources, BangumiAnimeService anime, NameResolutionService names) {
        this.sources = sources; this.anime = anime; this.names = names;
    }

    public Page page(long id, String kind, int page) {
        if (id < 1 || id > Integer.MAX_VALUE || !Set.of("CAST", "STAFF").contains(kind) || page < 1 || page > MAX_PAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "演职员类型或页码不正确（每类最多100页）");
        }
        anime.detail(id);
        String subjectUid = "anime:bangumi:" + id;
        int offset = (page - 1) * PAGE_SIZE;
        String path = "/p1/subjects/" + id + "/" + (kind.equals("CAST") ? "characters" : "staffs/persons") + "?limit=60&offset=" + offset;
        var snapshot = sources.fetch(path);
        JsonNode response = snapshot.data(), rows = response.path("data");
        int total = response.path("total").asInt(-1);
        if (!rows.isArray() || rows.size() > PAGE_SIZE || !response.path("total").isIntegralNumber() || !response.path("total").canConvertToInt() || total < 0) throw new ApiException(HttpStatus.BAD_GATEWAY, "CREDITS_INVALID_SOURCE", "来源分页结构发生变化，请稍后重试");
        var people = new LinkedHashMap<String, Entity>();
        var characters = new LinkedHashMap<String, Entity>();
        var staff = new LinkedHashMap<String, LinkedHashSet<Position>>();
        var cast = new LinkedHashMap<String, CastCredit>();
        int parsed = 0, omittedCredits = 0, unknownCastRows = 0;
        for (int index = 0; index < rows.size(); index++) {
            JsonNode row = rows.get(index);
            if (kind.equals("STAFF")) {
                JsonNode item = row.path("staff"); if (!valid(item)) continue;
                Entity person = entity(item, false, snapshot.fetchedAt()); people.put(person.id(), person); parsed++;
                var positions = staff.computeIfAbsent(person.id(), ignored -> new LinkedHashSet<>());
                if (!row.path("positions").isArray()) { omittedCredits++; continue; }
                for (var position : row.path("positions")) {
                    String code = position.path("type").path("id").asLong() > 0 ? position.path("type").path("id").asText() : "";
                    String label = text(position.path("type").path("cn"), 100);
                    if (code.isEmpty() && label == null) { omittedCredits++; continue; }
                    positions.add(new Position(code, label == null ? "来源未提供职务名" : label,
                        text(position.path("appearEps"), 200), text(position.path("summary"), 1000)));
                }
            } else {
                JsonNode item = row.path("character"); if (!valid(item)) continue;
                Entity character = entity(item, true, snapshot.fetchedAt()); characters.put(character.id(), character); parsed++;
                if (!row.path("casts").isArray()) { unknownCastRows++; continue; }
                for (int castIndex = 0; castIndex < row.path("casts").size(); castIndex++) {
                    JsonNode credit = row.path("casts").get(castIndex), personData = credit.path("person");
                    if (!valid(personData) || cast.size() >= MAX_CAST_CREDITS) { omittedCredits++; continue; }
                    Entity person = entity(personData, false, snapshot.fetchedAt()); people.put(person.id(), person);
                    Integer relation = credit.path("relation").isIntegralNumber() && credit.path("relation").canConvertToInt() ? credit.path("relation").asInt() : null;
                    String language = text(credit.path("language"), 80), version = text(credit.path("version"), 120);
                    String languageSource = language == null ? null : "language";
                    if (language == null && relation != null) {
                        language = switch (relation) { case 3 -> "zh"; case 4 -> "ja"; case 5 -> "en"; case 6 -> "ko"; default -> null; };
                        if (language != null) languageSource = "relation";
                    }
                    String relationLabel = castLabel(relation);
                    String performanceType = relation == null || relation < 0 || relation > 6 ? "UNKNOWN" : relation == 2 ? "LIVE_ACTION" : "VOICE";
                    String episode = text(credit.path("appearEps"), 200), description = text(credit.path("summary"), 1000);
                    String key = creditId(subjectUid, person.id(), character.id(), relation, language, version, episode, description);
                    cast.putIfAbsent(key, new CastCredit(key, subjectUid, person.id(), character.id(), relation, relationLabel, performanceType, language, languageSource, version, episode, description,
                        "data[" + index + "].casts[" + castIndex + "]"));
                }
            }
        }
        int expected = Math.max(0, Math.min(PAGE_SIZE, total - offset));
        int omittedRows = Math.abs(expected - rows.size()) + rows.size() - parsed;
        boolean exhausted = offset + PAGE_SIZE >= total;
        boolean capped = !exhausted && page == MAX_PAGE;
        Integer nextPage = !exhausted && !capped && !rows.isEmpty() ? page + 1 : null;
        String completeness = page == 1 && exhausted && omittedRows == 0 && omittedCredits == 0 && unknownCastRows == 0 ? "COMPLETE" : "PARTIAL";
        var coverage = new Coverage(page, PAGE_SIZE, total, rows.size(), parsed, omittedRows, omittedCredits, unknownCastRows, nextPage, exhausted, capped, completeness);
        String policy = names.policyVersion();
        var source = new Source("BANGUMI", "https://bgm.tv/subject/" + id + (kind.equals("CAST") ? "/characters" : "/persons"),
            path, ADAPTER_VERSION, snapshot.fetchedAt(), snapshot.cached(), snapshot.stale(), policy);
        return new Page(subjectUid, kind, List.copyOf(people.values()), List.copyOf(characters.values()),
            staff.entrySet().stream().map(e -> new StaffCredit(e.getKey(), List.copyOf(e.getValue()))).toList(), List.copyOf(cast.values()), coverage, source,
            hash(ADAPTER_VERSION + "|" + policy + "|" + path + "|" + response));
    }

    private Entity entity(JsonNode item, boolean character, Instant at) {
        var identity = new EntityIdentity(character ? EntityIdentity.Kind.CHARACTER : EntityIdentity.Kind.PERSON, item.path("id").asLong());
        var resolved = names.resolve(identity, item, at);
        String image = text(item.path("images").path("medium"), 1000);
        if (image == null || !image.matches("https://lain\\.bgm\\.tv/[^\\s]+")) image = "";
        JsonNode type = item.path(character ? "role" : "type");
        return new Entity(identity.canonicalUid(), resolved.displayName(), resolved.originalName(), image, character ? "CHARACTER" : "PERSON",
            type.isIntegralNumber() && type.canConvertToInt() ? type.asInt() : null, resolved.aliases(), resolved.assertions(), resolved.policyVersion(), identity.sourceUrl());
    }
    /** Official vendor/common/person_relations.json prsn_cv, verified 2026-10-20. CV does not imply Japanese. */
    private static String castLabel(Integer code) {
        if (code == null) return "来源未提供分类";
        return switch (code) {
            case 0 -> "CV 原声"; case 1 -> "译配"; case 2 -> "真人出演";
            case 3 -> "中配"; case 4 -> "日配"; case 5 -> "英配"; case 6 -> "韩配";
            default -> "未识别分类（" + code + "）";
        };
    }
    private static boolean valid(JsonNode item) { return item.path("id").isIntegralNumber() && item.path("id").canConvertToInt() && item.path("id").asInt() > 0; }
    private static String text(JsonNode node, int max) {
        if (!node.isTextual() || node.asText().isBlank()) return null;
        String value = node.asText().strip(); return value.substring(0, Math.min(max, value.length()));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String creditId(Object... values) {
        var canonical = new StringBuilder();
        for (Object value : values) {
            if (value == null) canonical.append("-1:");
            else { String text = value.toString(); canonical.append(text.length()).append(':').append(text); }
        }
        return hash(canonical.toString());
    }
}
