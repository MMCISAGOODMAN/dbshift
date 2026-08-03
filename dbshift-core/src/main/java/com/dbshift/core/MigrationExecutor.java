package com.dbshift.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.dbshift.dialect.DatabaseDialect;
import com.dbshift.model.MigrateResult;
import com.dbshift.model.MigrationFile;

public class MigrationExecutor {

    private final PlaceholderResolver placeholderResolver;

    public MigrationExecutor() {
        this(new PlaceholderResolver(null));
    }

    public MigrationExecutor(PlaceholderResolver placeholderResolver) {
        this.placeholderResolver = placeholderResolver != null
                ? placeholderResolver
                : new PlaceholderResolver(null);
    }

    public void executeSql(Connection conn, String sql) throws SQLException {
        List<String> statements = splitStatements(placeholderResolver.apply(sql));
        for (String statement : statements) {
            if (statement.isBlank() || statement.startsWith("--")) {
                continue;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(statement);
            }
        }
    }

    public MigrateResult executeMigration(Connection conn, MigrationFile file, String appliedBy,
                                          ChangelogRepository changelogRepository, DatabaseDialect dialect) {
        MigrateResult result = new MigrateResult();
        long start = System.currentTimeMillis();
        try {
            conn.setAutoCommit(false);
            executeSql(conn, file.getSql());
            if (file.isRepeatable()) {
                changelogRepository.upsertRepeatable(
                        conn, file, System.currentTimeMillis() - start, appliedBy, dialect);
            } else {
                changelogRepository.insert(
                        conn, file, System.currentTimeMillis() - start, appliedBy, dialect);
            }
            conn.commit();
            result.setSuccess(true);
            result.getExecutedVersions().add(file.getVersion());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setFailedVersion(file.getVersion());
            result.setErrorMessage(e.getMessage());
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                result.setErrorMessage(result.getErrorMessage() + "; rollback failed: " + rollbackEx.getMessage());
            }
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
        return result;
    }

    public MigrateResult executeRollback(Connection conn, MigrationFile rollbackFile, String migrationVersion,
                                         String appliedBy, ChangelogRepository changelogRepository) {
        MigrateResult result = new MigrateResult();
        try {
            conn.setAutoCommit(false);
            executeSql(conn, rollbackFile.getSql());
            changelogRepository.deleteVersion(conn, migrationVersion);
            conn.commit();
            result.setSuccess(true);
            result.getExecutedVersions().add(rollbackFile.getVersion());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setFailedVersion(rollbackFile.getVersion());
            result.setErrorMessage(e.getMessage());
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                result.setErrorMessage(result.getErrorMessage() + "; rollback failed: " + rollbackEx.getMessage());
            }
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
        return result;
    }

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(c);
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
                continue;
            }
            if (c == ';' && !inSingle && !inDouble) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }
}
