package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;

import com.dbshift.config.EnvironmentConfig;

public final class H2Dialect extends AbstractDatabaseDialect {

    @Override
    public DialectType type() {
        return DialectType.H2;
    }

    @Override
    public String displayName() {
        return "H2";
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
