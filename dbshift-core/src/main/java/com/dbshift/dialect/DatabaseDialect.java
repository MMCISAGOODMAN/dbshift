package com.dbshift.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import com.dbshift.config.EnvironmentConfig;

public interface DatabaseDialect {

    DialectType type();

    String displayName();

    String createChangelogTableSql();

    String createLockTableSql();

    /**
     * WHERE clause fragment for filtering successful migration rows.
     */
    String successFilter();

    /** Literal used when inserting a successful changelog row. */
    String successLiteral();

    String lockedTrueLiteral();

    String lockedFalseLiteral();

    boolean supportsDdlInTransaction();

    Optional<String> ddlTransactionWarning();

    Optional<String> alterDdlNote();

    MetadataParams metadataParams(Connection conn, EnvironmentConfig config) throws SQLException;
}
