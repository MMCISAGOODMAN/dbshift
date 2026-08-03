package com.dbshift.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.dbshift.config.EnvironmentConfig;
import com.dbshift.dialect.DatabaseDialect;
import com.dbshift.dialect.MetadataParams;
import com.dbshift.model.ChangelogEntry;
import com.dbshift.model.MigrationFile;
import com.dbshift.model.Version;

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
        String[] names = {
                TABLE_NAME,
                TABLE_NAME.toUpperCase(),
                TABLE_NAME.toLowerCase()
        };
        for (String name : names) {
            try (ResultSet rs = conn.getMetaData().getTables(
                    params.catalog(), params.schemaPattern(), name, new String[]{"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<ChangelogEntry> findAllApplied(Connection conn, DatabaseDialect dialect) throws SQLException {
        List<ChangelogEntry> entries = new ArrayList<>();
        String sql = """
                SELECT version, description, checksum, applied_at, applied_by, execution_ms, success, script_type
                FROM schema_changelog
                WHERE %s AND script_type IN ('MIGRATION', 'BASELINE')
                """.formatted(dialect.successFilter());
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
        }
        entries.sort(Comparator.comparing(e -> {
            try {
                return Version.parse(e.getVersion());
            } catch (Exception ex) {
                return Version.ofParts(0);
            }
        }));
        return entries;
    }

    public List<ChangelogEntry> findRepeatables(Connection conn, DatabaseDialect dialect) throws SQLException {
        List<ChangelogEntry> entries = new ArrayList<>();
        String sql = """
                SELECT version, description, checksum, applied_at, applied_by, execution_ms, success, script_type
                FROM schema_changelog
                WHERE %s AND script_type = 'REPEATABLE'
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

    public List<ChangelogEntry> findFailed(Connection conn) throws SQLException {
        List<ChangelogEntry> entries = new ArrayList<>();
        String sql = """
                SELECT version, description, checksum, applied_at, applied_by, execution_ms, success, script_type
                FROM schema_changelog
                WHERE success = FALSE OR success = 0
                ORDER BY version
                """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
        } catch (SQLException e) {
            // Some dialects may not accept FALSE; retry with 0-only already covered by OR
            throw e;
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

    public void insertBaseline(Connection conn, Version version, String appliedBy, DatabaseDialect dialect)
            throws SQLException {
        MigrationFile baseline = new MigrationFile(
                version.display(),
                "<< Baseline >>",
                null,
                "0",
                MigrationFile.ScriptType.BASELINE,
                ""
        );
        insert(conn, baseline, 0, appliedBy, dialect);
    }

    public void upsertRepeatable(Connection conn, MigrationFile file, long executionMs, String appliedBy,
                                 DatabaseDialect dialect) throws SQLException {
        deleteVersion(conn, file.getVersion());
        insert(conn, file, executionMs, appliedBy, dialect);
    }

    public void updateChecksum(Connection conn, String version, String checksum) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE schema_changelog SET checksum = ? WHERE version = ?")) {
            ps.setString(1, checksum);
            ps.setString(2, version);
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

    public Optional<Version> baselineVersion(Connection conn) throws SQLException {
        String sql = "SELECT version FROM schema_changelog WHERE script_type = 'BASELINE' ORDER BY version";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Version max = null;
            while (rs.next()) {
                Version current = Version.parse(rs.getString(1));
                if (max == null || current.compareTo(max) > 0) {
                    max = current;
                }
            }
            return Optional.ofNullable(max);
        }
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
