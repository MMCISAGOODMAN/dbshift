package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;

import com.dbshift.config.EnvironmentConfig;

public final class OracleDialect extends AbstractDatabaseDialect {

    private static final String CHANGELOG_DDL = """
            BEGIN
                EXECUTE IMMEDIATE '
                    CREATE TABLE schema_changelog (
                        version VARCHAR2(32) PRIMARY KEY,
                        description VARCHAR2(255) NOT NULL,
                        checksum VARCHAR2(64) NOT NULL,
                        applied_at TIMESTAMP NOT NULL,
                        applied_by VARCHAR2(64) NOT NULL,
                        execution_ms NUMBER(10) NOT NULL,
                        success NUMBER(1) DEFAULT 1 NOT NULL,
                        script_type VARCHAR2(16) DEFAULT ''MIGRATION'' NOT NULL
                    )';
            EXCEPTION
                WHEN OTHERS THEN
                    IF SQLCODE != -955 THEN
                        RAISE;
                    END IF;
            END;
            """;

    @Override
    public DialectType type() {
        return DialectType.ORACLE;
    }

    @Override
    public String displayName() {
        return "Oracle";
    }

    @Override
    public String createChangelogTableSql() {
        return CHANGELOG_DDL;
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
    public MetadataParams metadataParams(Connection conn, EnvironmentConfig config) throws SQLException {
        String schema = config.getSchema();
        if (schema != null && !schema.isBlank()) {
            return new MetadataParams(null, schema.toUpperCase());
        }
        String user = conn.getMetaData().getUserName();
        return new MetadataParams(null, user != null ? user.toUpperCase() : null);
    }
}
