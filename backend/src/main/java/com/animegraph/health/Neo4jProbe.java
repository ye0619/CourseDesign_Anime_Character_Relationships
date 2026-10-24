package com.animegraph.health;

import java.time.Duration;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Neo4jProbe implements DependencyProbe {
    private final Driver driver;
    private final String database;

    public Neo4jProbe(Driver driver, @Value("${app.neo4j.database}") String database) {
        this.driver = driver;
        this.database = database;
    }

    public String name() { return "neo4j"; }

    public boolean isAvailable() {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            return session.run("RETURN 1 AS value", TransactionConfig.builder()
                    .withTimeout(Duration.ofSeconds(2)).build()).single().get("value").asInt() == 1;
        } catch (Neo4jException exception) {
            return false;
        }
    }
}
