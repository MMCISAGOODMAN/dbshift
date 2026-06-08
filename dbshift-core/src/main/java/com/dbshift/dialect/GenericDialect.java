package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;

import com.dbshift.config.EnvironmentConfig;

/**
 * Fallback dialect using ANSI-ish SQL and JDBC metadata. Suitable when the
 * database is unknown or not explicitly configured.
 */
public final class GenericDialect extends AbstractDatabaseDialect {

    @Override
    public DialectType type() {
        return DialectType.GENERIC;
    }

    @Override
    public String displayName() {
        return "Generic JDBC";
    }

    @Override
    public MetadataParams metadataParams(Connection conn, EnvironmentConfig config) throws SQLException {
        String schema = config.getSchema();
        if (schema != null && !schema.isBlank()) {
            return new MetadataParams(conn.getCatalog(), schema);
        }
        return new MetadataParams(conn.getCatalog(), conn.getSchema());
    }
}
