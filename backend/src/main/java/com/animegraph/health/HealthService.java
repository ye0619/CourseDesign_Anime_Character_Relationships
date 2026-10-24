package com.animegraph.health;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final List<DependencyProbe> probes;

    public HealthService(List<DependencyProbe> probes) {
        this.probes = probes;
    }

    public Readiness readiness() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        for (DependencyProbe probe : probes) {
            dependencies.put(probe.name(), probe.isAvailable() ? "UP" : "DOWN");
        }
        boolean ready = dependencies.keySet().containsAll(List.of("mysql", "neo4j"))
                && dependencies.values().stream().allMatch("UP"::equals);
        return new Readiness(ready ? "READY" : "NOT_READY", dependencies, Instant.now());
    }

    public record Readiness(String status, Map<String, String> dependencies, Instant checkedAt) {
        public boolean ready() { return "READY".equals(status); }
    }
}
