package com.animegraph.health;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthServiceTest {
    @Test
    void readyRequiresBothDependencies() {
        assertThat(new HealthService(List.of(probe("mysql", true), probe("neo4j", true))).readiness().ready()).isTrue();
        assertThat(new HealthService(List.of(probe("mysql", true), probe("neo4j", false))).readiness().ready()).isFalse();
        assertThat(new HealthService(List.of(probe("mysql", false), probe("neo4j", true))).readiness().ready()).isFalse();
        assertThat(new HealthService(List.of()).readiness().ready()).isFalse();
    }

    private DependencyProbe probe(String name, boolean available) {
        return new DependencyProbe() {
            public String name() { return name; }
            public boolean isAvailable() { return available; }
        };
    }
}
