package com.dbshift.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.dbshift.dialect.DatabaseDialect;
import com.dbshift.model.MigrateResult;
import com.dbshift.model.MigrationFile;

public class MigrationExecutor {

    public void executeSql(Connection conn, String sql) throws SQLException {
        List<String> statements = splitStatements(sql);
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
            changelogRepository.insert(conn, file, System.currentTimeMillis() - start, appliedBy, dialect);
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
        long start = System.currentTimeMillis();
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
        return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
