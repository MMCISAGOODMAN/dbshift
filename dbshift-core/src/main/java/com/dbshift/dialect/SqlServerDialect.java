package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;

import com.dbshift.config.EnvironmentConfig;

public final class SqlServerDialect extends AbstractDatabaseDialect {

    private static final String CHANGELOG_DDL = """
            IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'schema_changelog')
            CREATE TABLE schema_changelog (
                version VARCHAR(100) PRIMARY KEY,
                description VARCHAR(255) NOT NULL,
                checksum VARCHAR(64) NOT NULL,
                applied_at DATETIME2 NOT NULL,
                applied_by VARCHAR(64) NOT NULL,
                execution_ms INT NOT NULL,
                success BIT NOT NULL DEFAULT 1,
                script_type VARCHAR(16) NOT NULL DEFAULT 'MIGRATION'
            )
            """;

    private static final String LOCK_DDL = """
            IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'schema_changelog_lock')
            CREATE TABLE schema_changelog_lock (
                id INT PRIMARY KEY,
                locked BIT NOT NULL,
                locked_by VARCHAR(255),
                locked_at DATETIME2
            )
            """;

    @Override
    public DialectType type() {
        return DialectType.SQLSERVER;
    }

    @Override
    public String displayName() {
        return "SQL Server";
    }

    @Override
    public String createChangelogTableSql() {
        return CHANGELOG_DDL;
    }

    @Override
    public String createLockTableSql() {
        return LOCK_DDL;
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
    public boolean supportsDdlInTransaction() {
        return false;
    }

    @Override
    public java.util.Optional<String> ddlTransactionWarning() {
        return java.util.Optional.of("SQL Server DDL may auto-commit; failed migrations may leave schema changes applied.");
    }

    @Override
    public MetadataParams metadataParams(Connection conn, EnvironmentConfig config) throws SQLException {
        String schema = config.getSchema();
        if (schema != null && !schema.isBlank()) {
            return new MetadataParams(conn.getCatalog(), schema);
        }
        return new MetadataParams(conn.getCatalog(), "dbo");
    }
}
