package com.dbshift.dialect;

import java.util.List;

import com.dbshift.config.EnvironmentConfig;

public final class DialectResolver {

    private static final List<String> SUPPORTED_DIALECTS = List.of(
            "mysql", "mariadb", "postgresql", "h2", "sqlite", "sqlserver", "oracle", "generic"
    );

    private DialectResolver() {
    }

    public static List<String> supportedDialects() {
        return SUPPORTED_DIALECTS;
    }

    public static DatabaseDialect resolve(EnvironmentConfig config) {
        String explicit = config.getDialect();
        if (explicit != null && !explicit.isBlank()) {
            return fromName(explicit.trim());
        }
        return fromUrl(config.getUrl());
    }

    public static DatabaseDialect fromName(String name) {
        String normalized = name.toLowerCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "mysql" -> new MySqlDialect();
            case "mariadb" -> new MariaDbDialect();
            case "postgresql", "postgres", "pg" -> new PostgreSqlDialect();
            case "h2" -> new H2Dialect();
            case "sqlite", "sqlite3" -> new SqliteDialect();
            case "sqlserver", "mssql", "sql_server" -> new SqlServerDialect();
            case "oracle" -> new OracleDialect();
            case "generic", "jdbc" -> new GenericDialect();
            default -> throw new IllegalArgumentException(
                    "Unknown dialect: " + name + ". Supported: " + String.join(", ", SUPPORTED_DIALECTS));
        };
    }

    public static DatabaseDialect fromUrl(String url) {
        if (url == null || url.isBlank()) {
            return new GenericDialect();
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:mysql:")) {
            return new MySqlDialect();
        }
        if (lower.startsWith("jdbc:mariadb:")) {
            return new MariaDbDialect();
        }
        if (lower.startsWith("jdbc:postgresql:") || lower.startsWith("jdbc:pgsql:")) {
            return new PostgreSqlDialect();
        }
        if (lower.startsWith("jdbc:h2:")) {
            return new H2Dialect();
        }
        if (lower.startsWith("jdbc:sqlite:")) {
            return new SqliteDialect();
        }
        if (lower.startsWith("jdbc:sqlserver:") || lower.startsWith("jdbc:microsoft:sqlserver:")) {
            return new SqlServerDialect();
        }
        if (lower.startsWith("jdbc:oracle:")) {
            return new OracleDialect();
        }
        return new GenericDialect();
    }
}
