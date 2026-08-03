package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import com.dbshift.config.EnvironmentConfig;

abstract class AbstractDatabaseDialect implements DatabaseDialect {

    private static final String CHANGELOG_DDL = """
            CREATE TABLE IF NOT EXISTS schema_changelog (
                version VARCHAR(100) PRIMARY KEY,
                description VARCHAR(255) NOT NULL,
                checksum VARCHAR(64) NOT NULL,
                applied_at TIMESTAMP NOT NULL,
                applied_by VARCHAR(64) NOT NULL,
                execution_ms INT NOT NULL,
                success BOOLEAN NOT NULL DEFAULT TRUE,
                script_type VARCHAR(16) NOT NULL DEFAULT 'MIGRATION'
            )
            """;

    private static final String LOCK_DDL = """
            CREATE TABLE IF NOT EXISTS schema_changelog_lock (
                id INT PRIMARY KEY,
                locked BOOLEAN NOT NULL,
                locked_by VARCHAR(255),
                locked_at TIMESTAMP
            )
            """;

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
        return "success = TRUE";
    }

    @Override
    public String successLiteral() {
        return "TRUE";
    }

    @Override
    public String lockedTrueLiteral() {
        return "TRUE";
    }

    @Override
    public String lockedFalseLiteral() {
        return "FALSE";
    }

    @Override
    public boolean supportsDdlInTransaction() {
        return true;
    }

    @Override
    public Optional<String> ddlTransactionWarning() {
        return Optional.empty();
    }

    @Override
    public Optional<String> alterDdlNote() {
        return Optional.empty();
    }

    protected MetadataParams catalogOnly(Connection conn) throws SQLException {
        return new MetadataParams(conn.getCatalog(), null);
    }
}
