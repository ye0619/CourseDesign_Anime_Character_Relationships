package com.animegraph.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/** Resolves display policy without mutating source documents or provider IDs. */
@Service
public class NameResolutionService {
    public record LocalName(String id,String text,String locale,String kind,String source,String reviewStatus) {}
    public static void validateLocal(List<LocalName> names,String preferred) {
        if(names==null||names.isEmpty()||names.size()>30)throw localBad("请填写1至30个有效名称");
        var ids=new HashSet<String>();var texts=new HashSet<String>();
        for(var n:names) {
            if(n==null||n.id()==null||!n.id().matches("[A-Za-z0-9_-]{1,80}")||!ids.add(n.id())||n.text()==null||n.text().isBlank()||n.text().length()>200||n.locale()==null||!n.locale().matches("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{1,8})*")||n.kind()==null||!Set.of("PRIMARY","ORIGINAL","ALIAS").contains(n.kind()))throw localBad("名称、语言、类型或编号无效");
            if(!texts.add(n.locale().toLowerCase(Locale.ROOT)+"|"+n.text().strip()))throw localBad("同语言名称重复，请合并来源");
        }
        if(preferred!=null&&!preferred.isBlank()&&!ids.contains(preferred))throw localBad("指定显示名称不在名称集合中");
    }
    public static String localDisplay(List<LocalName> names,String preferred,List<String> originalLanguages) {
        validateLocal(names,preferred);
        if(preferred!=null&&!preferred.isBlank())return names.stream().filter(n->n.id().equals(preferred)).findFirst().orElseThrow().text().strip();
        return names.stream().min(Comparator.comparingInt((LocalName n)->n.locale().toLowerCase(Locale.ROOT).startsWith("zh")?0:originalLanguages.contains(n.locale())?1:n.kind().equals("ORIGINAL")||n.kind().equals("PRIMARY")?2:3)).orElseThrow().text().strip();
    }
    private static com.animegraph.common.ApiException localBad(String message){return new com.animegraph.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"NAME_INVALID",message);}

    public record NameAssertion(String name, String locale, String kind, String provider, String sourceUrl,
                                String sourceRevision, Instant retrievedAt, String reviewStatus, String reviewedOn) {}
    public record ResolvedName(String displayName, String originalName, List<String> aliases,
                               List<NameAssertion> assertions, String policyVersion) {}
    private record Rule(String entityUid, String locale, String preferredName, String reviewStatus, boolean enabled,
                        String reviewedOn, String sourceUrl, List<String> sourceAliases, String reason) {}
    private record Rules(String version, List<Rule> rules) {}
    private final String policyVersion;
    private final Map<String, Rule> preferred;

    public String policyVersion() { return policyVersion; }

    public NameResolutionService(ObjectMapper json) {
        try (var stream = NameResolutionService.class.getResourceAsStream("/identity/name-rules.json")) {
            if (stream == null) throw new IllegalStateException("Missing reviewed name policy");
            var config = json.readValue(stream, Rules.class);
            policyVersion = Objects.requireNonNull(config.version());
            var rules = new HashMap<String, Rule>();
            for (var rule : config.rules()) {
                if (!rule.entityUid().matches("(character|person|anime):bangumi:[1-9][0-9]{0,9}")
                    || !rule.sourceUrl().equals(sourceUrl(rule.entityUid())) || rule.preferredName().isBlank()
                    || rule.preferredName().length() > 200 || rule.reason().isBlank() || rule.sourceAliases() == null
                    || rule.sourceAliases().size() > 20 || rule.sourceAliases().stream().anyMatch(alias -> clean(alias).isBlank())) {
                    throw new IllegalStateException("Invalid reviewed name policy");
                }
                String[] identity = rule.entityUid().split(":");
                new EntityIdentity(EntityIdentity.Kind.valueOf(identity[0].toUpperCase(Locale.ROOT)), Long.parseLong(identity[2]));
                LocalDate.parse(rule.reviewedOn());
                if (rule.enabled() && "REVIEWED".equals(rule.reviewStatus()) && "zh-Hans".equals(rule.locale())) {
                    if (rules.put(rule.entityUid(), rule) != null) throw new IllegalStateException("Duplicate preferred name policy");
                }
            }
            preferred = Map.copyOf(rules);
        } catch (IOException e) { throw new IllegalStateException("Cannot read reviewed name policy", e); }
    }

    public ResolvedName resolve(EntityIdentity identity, JsonNode source, Instant retrievedAt) {
        if (source.path("id").asLong() != identity.providerId()) throw new IllegalArgumentException("Source identity mismatch");
        var assertions = new ArrayList<NameAssertion>();
        String original = text(source.path("name"));
        String chinese = text(source.path("nameCN"));
        if (chinese.isBlank()) chinese = text(source.path("name_cn"));
        if (chinese.isBlank()) chinese = infoboxChinese(source);
        addSource(assertions, identity, original, "und", "ORIGINAL", retrievedAt);
        addSource(assertions, identity, chinese, "zh", "SOURCE_CHINESE", retrievedAt);
        for (var alias : source.path("aliases")) {
            if (alias.isTextual()) addSource(assertions, identity, text(alias), "und", "ALIAS", retrievedAt);
        }
        // Compatible with public v0 infobox documents when supplied by a future
        // admitted metadata adapter. No per-node remote requests are started here.
        for (var field : source.path("infobox")) {
            String key = text(field.path("key"));
            if (!Set.of("别名", "中文名", "简体中文名", "繁体中文名").contains(key)) continue;
            JsonNode value = field.path("value");
            if (value.isTextual()) addSource(assertions, identity, text(value), key.contains("中文") ? "zh" : "und", "ALIAS", retrievedAt);
            else if (value.isArray()) for (var alias : value) {
                String type = text(alias.path("k"));
                addSource(assertions, identity, alias.isTextual() ? text(alias) : text(alias.path("v")),
                    type.contains("中文") ? "zh" : type.equals("英文名") ? "en" : "und", "ALIAS", retrievedAt);
            }
        }
        String display = chinese.isBlank() ? original.isBlank() ? "未命名实体" : original : chinese;
        Rule rule = preferred.get(identity.canonicalUid());
        if (rule != null) {
            display = rule.preferredName();
            assertions.add(new NameAssertion(display, rule.locale(), "PROJECT_PREFERRED", "PROJECT_POLICY", rule.sourceUrl(),
                null, null, "REVIEWED", rule.reviewedOn()));
            for (String alias : rule.sourceAliases()) {
                String value = clean(alias);
                if (!value.isBlank()) assertions.add(new NameAssertion(value, "und", "REVIEWED_ALIAS", "BANGUMI", rule.sourceUrl(),
                    null, null, "REVIEWED", rule.reviewedOn()));
            }
        }
        var aliases = new LinkedHashSet<String>();
        for (var assertion : assertions) if (!assertion.name().equals(display)) aliases.add(assertion.name());
        return new ResolvedName(display, original, List.copyOf(aliases), List.copyOf(assertions), policyVersion);
    }

    private static void addSource(List<NameAssertion> names, EntityIdentity identity, String value, String locale, String kind, Instant at) {
        if (value.isBlank() || names.size() >= 40 || names.stream().anyMatch(n -> n.name().equals(value) && n.kind().equals(kind))) return;
        names.add(new NameAssertion(value, locale, kind, "BANGUMI", identity.sourceUrl(), null, at, "SOURCE", null));
    }
    private static String text(JsonNode node) {
        if (!node.isTextual()) return "";
        return clean(node.asText());
    }
    private static String clean(String input) {
        String value = input == null ? "" : input.strip();
        return value.length() <= 200 ? value : "";
    }
    private static String infoboxChinese(JsonNode source) {
        for (var field : source.path("infobox")) {
            String key = text(field.path("key"));
            if (Set.of("简体中文名", "中文名").contains(key) && !text(field.path("value")).isBlank()) return text(field.path("value"));
            if (key.equals("别名") && field.path("value").isArray()) for (var alias : field.path("value")) {
                if (Set.of("简体中文名", "中文名").contains(text(alias.path("k"))) && !text(alias.path("v")).isBlank()) return text(alias.path("v"));
            }
        }
        return "";
    }
    private static String sourceUrl(String uid) {
        String[] parts = uid.split(":");
        return "https://bgm.tv/" + (parts[0].equals("anime") ? "subject" : parts[0]) + "/" + parts[2];
    }
}
