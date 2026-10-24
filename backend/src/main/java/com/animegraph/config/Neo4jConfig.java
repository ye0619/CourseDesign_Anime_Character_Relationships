package com.animegraph.config;

import java.util.concurrent.TimeUnit;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {
    @Bean(destroyMethod = "close")
    Driver neo4jDriver(@Value("${app.neo4j.uri}") String uri,
                       @Value("${app.neo4j.username}") String username,
                       @Value("${app.neo4j.password}") String password) {
        Config config = Config.builder()
                .withConnectionTimeout(2, TimeUnit.SECONDS)
                .withConnectionAcquisitionTimeout(2, TimeUnit.SECONDS)
                .withMaxTransactionRetryTime(0, TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(10)
                .build();
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
    }
}
