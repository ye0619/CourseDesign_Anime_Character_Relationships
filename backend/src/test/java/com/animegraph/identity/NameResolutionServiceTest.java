package com.animegraph.identity;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NameResolutionServiceTest {
    final ObjectMapper json = new ObjectMapper();
    final NameResolutionService service = new NameResolutionService(json);
    final Instant at = Instant.parse("2026-10-20T00:00:00Z");

    @Test void reviewedPreferredNameKeepsOriginalSourceAndAliasesWithoutMutation() throws Exception {
        var source = json.readTree("""
            {"id":125172,"name":"結束バンド","nameCN":"纽带乐队","role":4}
            """);
        var original = source.deepCopy();
        var result = service.resolve(new EntityIdentity(EntityIdentity.Kind.CHARACTER, 125172), source, at);
        assertThat(result.displayName()).isEqualTo("结束乐队");
        assertThat(result.originalName()).isEqualTo("結束バンド");
        assertThat(result.aliases()).contains("纽带乐队", "結束バンド", "Kessoku Band").doesNotContain("结束乐队");
        assertThat(result.assertions()).anyMatch(a -> a.kind().equals("PROJECT_PREFERRED") && a.provider().equals("PROJECT_POLICY") && a.reviewStatus().equals("REVIEWED"));
        assertThat(result.assertions()).anyMatch(a -> a.kind().equals("SOURCE_CHINESE") && a.name().equals("纽带乐队") && a.retrievedAt().equals(at));
        assertThat(result.policyVersion()).isEqualTo("names-2026-10-20.1");
        assertThat(source).isEqualTo(original);
    }
    @Test void numericIdsAndSimilarNamesNeverMergeAcrossTypesOrOtherEntities() throws Exception {
        var sameNumber = json.readTree("{\"id\":125172,\"name\":\"結束バンド\",\"nameCN\":\"纽带乐队\"}");
        for (var kind : new EntityIdentity.Kind[]{EntityIdentity.Kind.PERSON, EntityIdentity.Kind.ANIME}) {
            assertThat(service.resolve(new EntityIdentity(kind, 125172), sameNumber, at).displayName()).isEqualTo("纽带乐队");
        }
        var another = json.readTree("{\"id\":125173,\"name\":\"結束バンド\",\"nameCN\":\"纽带乐队\"}");
        assertThat(service.resolve(new EntityIdentity(EntityIdentity.Kind.CHARACTER, 125173), another, at).displayName()).isEqualTo("纽带乐队");
        assertThat(new EntityIdentity(EntityIdentity.Kind.CHARACTER, 125172).canonicalUid()).isEqualTo("character:bangumi:125172");
        assertThat(new EntityIdentity(EntityIdentity.Kind.ANIME, 125172).canonicalUid()).isEqualTo("anime:bangumi:125172");
        assertThat(new EntityIdentity(EntityIdentity.Kind.ANIME, 125172).sourceUrl()).isEqualTo("https://bgm.tv/subject/125172");
    }
    @Test void publicInfoboxAliasesAndUnknownLanguageAreParsedConservatively() throws Exception {
        var source = json.readTree("""
          {"id":7,"name":"原名","aliases":["旧名",{"unexpected":"skip"}],"infobox":[
            {"key":"别名","value":[{"k":"简体中文名","v":"中文名"},{"k":"英文名","v":"English"},{"k":"第二中文名","v":"另一译名"}]},
            {"key":"简介","value":"简介不是名字"}]}
          """);
        var result = service.resolve(new EntityIdentity(EntityIdentity.Kind.CHARACTER, 7), source, at);
        assertThat(result.displayName()).isEqualTo("中文名");
        assertThat(result.aliases()).contains("原名", "旧名", "English", "另一译名").doesNotContain("简介不是名字");
        assertThat(result.assertions()).anyMatch(a -> a.name().equals("English") && a.locale().equals("en"));
        assertThat(result.assertions()).allMatch(a -> a.sourceRevision() == null);
    }
    @Test void refreshCannotOverrideReviewedPreferenceAndNamesRemainBounded() throws Exception {
        var source = json.createObjectNode().put("id", 125172).put("name", "x".repeat(201)).put("nameCN", "来源新名称");
        var aliases = source.putArray("aliases");
        for (int i = 0; i < 100; i++) aliases.add("别名" + i);
        var result = service.resolve(new EntityIdentity(EntityIdentity.Kind.CHARACTER, 125172), source, at.plusSeconds(300));
        assertThat(result.displayName()).isEqualTo("结束乐队");
        assertThat(result.aliases()).hasSizeLessThanOrEqualTo(46).contains("来源新名称");
        assertThat(result.originalName()).isEmpty();
    }
    @Test void wrongProviderIdAndInvalidIdentityAreRejected() throws Exception {
        assertThatThrownBy(() -> service.resolve(new EntityIdentity(EntityIdentity.Kind.CHARACTER, 7), json.readTree("{\"id\":8}"), at)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntityIdentity(EntityIdentity.Kind.PERSON, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EntityIdentity(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
