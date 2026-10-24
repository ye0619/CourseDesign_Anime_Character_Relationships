package com.animegraph.charactergraph;

import static org.assertj.core.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RelationTypeTest {
    @Test
    void officialDictionaryAndInverseDirectionsAreComplete() {
        var official=Arrays.stream(RelationType.values()).map(RelationType::definition).filter(d -> !d.legacy()).toList();
        assertThat(official).hasSize(28);
        assertThat(official).extracting(RelationType.Definition::bangumiId).doesNotHaveDuplicates();
        assertThat(RelationType.CRUSH.directed()).isTrue();
        assertThat(RelationType.FRIEND.directed()).isFalse();
        assertThat(RelationType.TEACHER.definition().inverseCode()).isEqualTo("STUDENT");
        official.stream().filter(d -> d.inverseCode()!=null).forEach(d ->
            assertThat(RelationType.valueOf(d.inverseCode()).definition().inverseCode()).isEqualTo(d.code()));
        assertThat(RelationType.MENTOR.definition().legacy()).isTrue();
        assertThat(RelationType.parse("UNKNOWN")).isEmpty();
    }
}
