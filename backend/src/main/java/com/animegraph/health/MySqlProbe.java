package com.animegraph.health;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class MySqlProbe implements DependencyProbe {
    private final DataSource dataSource;

    public MySqlProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String name() { return "mysql"; }

    public boolean isAvailable() {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (var result = statement.executeQuery("SELECT 1")) {
                return result.next() && result.getInt(1) == 1;
            }
        } catch (SQLException exception) {
            return false;
        }
    }
}
