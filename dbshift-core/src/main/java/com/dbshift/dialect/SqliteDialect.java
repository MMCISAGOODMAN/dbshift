package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;

import com.dbshift.config.EnvironmentConfig;

public final class SqliteDialect extends AbstractDatabaseDialect {

    @Override
    public DialectType type() {
        return DialectType.SQLITE;
    }

    @Override
    public String displayName() {
        return "SQLite";
    }

    @Override
    public String successFilter() {
        return "success = 1";
    }

    @Override
    public String successLiteral() {
        return "1";
    }

    @Override
    public String lockedTrueLiteral() {
        return "1";
    }

    @Override
    public String lockedFalseLiteral() {
        return "0";
    }

    @Override
    public MetadataParams metadataParams(Connection conn, EnvironmentConfig config) throws SQLException {
        String schema = config.getSchema();
        if (schema != null && !schema.isBlank()) {
            return new MetadataParams(null, schema);
        }
        String connSchema = conn.getSchema();
        return new MetadataParams(null, connSchema != null ? connSchema : "main");
    }
}
