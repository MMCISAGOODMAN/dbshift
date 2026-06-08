package com.dbshift.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.dbshift.config.EnvironmentConfig;
import com.dbshift.dialect.DatabaseDialect;
import com.dbshift.dialect.MetadataParams;
import com.dbshift.model.ChangelogEntry;
import com.dbshift.model.MigrationFile;

public class ChangelogRepository {

    public static final String TABLE_NAME = "schema_changelog";

    public void initTable(Connection conn, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(dialect.createChangelogTableSql());
        }
    }

    public boolean tableExists(Connection conn, DatabaseDialect dialect, EnvironmentConfig config)
            throws SQLException {
        MetadataParams params = dialect.metadataParams(conn, config);
        try (ResultSet rs = conn.getMetaData().getTables(
                params.catalog(), params.schemaPattern(), TABLE_NAME, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public List<ChangelogEntry> findAllApplied(Connection conn, DatabaseDialect dialect) throws SQLException {
        List<ChangelogEntry> entries = new ArrayList<>();
        String sql = """
                SELECT version, description, checksum, applied_at, applied_by, execution_ms, success, script_type
                FROM schema_changelog
                WHERE %s AND script_type = 'MIGRATION'
                ORDER BY version
                """.formatted(dialect.successFilter());
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
        }
        return entries;
    }

    public Optional<ChangelogEntry> findByVersion(Connection conn, String version) throws SQLException {
        String sql = "SELECT version, description, checksum, applied_at, applied_by, execution_ms, success, script_type FROM schema_changelog WHERE version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapEntry(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void insert(Connection conn, MigrationFile file, long executionMs, String appliedBy,
                       DatabaseDialect dialect) throws SQLException {
        String sql = """
                INSERT INTO schema_changelog (version, description, checksum, applied_at, applied_by, execution_ms, success, script_type)
                VALUES (?, ?, ?, ?, ?, ?, %s, ?)
                """.formatted(dialect.successLiteral());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, file.getVersion());
            ps.setString(2, file.getDescription());
            ps.setString(3, file.getChecksum());
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(5, appliedBy);
            ps.setLong(6, executionMs);
            ps.setString(7, file.getScriptType().name());
            ps.executeUpdate();
        }
    }

    public void deleteVersion(Connection conn, String version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM schema_changelog WHERE version = ?")) {
            ps.setString(1, version);
            ps.executeUpdate();
        }
    }

    public Optional<String> latestAppliedVersion(Connection conn, DatabaseDialect dialect) throws SQLException {
        List<ChangelogEntry> entries = findAllApplied(conn, dialect);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(entries.size() - 1).getVersion());
    }

    private ChangelogEntry mapEntry(ResultSet rs) throws SQLException {
        ChangelogEntry entry = new ChangelogEntry();
        entry.setVersion(rs.getString("version"));
        entry.setDescription(rs.getString("description"));
        entry.setChecksum(rs.getString("checksum"));
        Timestamp appliedAt = rs.getTimestamp("applied_at");
        if (appliedAt != null) {
            entry.setAppliedAt(appliedAt.toLocalDateTime());
        }
        entry.setAppliedBy(rs.getString("applied_by"));
        entry.setExecutionMs(rs.getLong("execution_ms"));
        entry.setSuccess(rs.getBoolean("success"));
        entry.setScriptType(rs.getString("script_type"));
        return entry;
    }
}
