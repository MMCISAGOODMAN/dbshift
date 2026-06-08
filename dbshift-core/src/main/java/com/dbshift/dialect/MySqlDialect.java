package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;

import com.dbshift.config.EnvironmentConfig;

public class MySqlDialect extends AbstractDatabaseDialect {

    @Override
    public DialectType type() {
        return DialectType.MYSQL;
    }

    @Override
    public String displayName() {
        return "MySQL";
    }

    @Override
    public boolean supportsDdlInTransaction() {
        return false;
    }

    @Override
    public java.util.Optional<String> ddlTransactionWarning() {
        return java.util.Optional.of("MySQL DDL auto-commits; failed migrations may leave schema changes applied.");
    }

    @Override
    public java.util.Optional<String> alterDdlNote() {
        return java.util.Optional.of("MySQL DDL may auto-commit; review carefully.");
    }

    @Override
    public MetadataParams metadataParams(Connection conn, EnvironmentConfig config) throws SQLException {
        String schema = config.getSchema();
        if (schema != null && !schema.isBlank()) {
            return new MetadataParams(conn.getCatalog(), schema);
        }
        return catalogOnly(conn);
    }
}
