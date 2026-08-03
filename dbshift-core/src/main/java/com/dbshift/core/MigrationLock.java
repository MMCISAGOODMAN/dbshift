package com.dbshift.core;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.dbshift.dialect.DatabaseDialect;

public class MigrationLock {

    public static final String TABLE_NAME = "schema_changelog_lock";
    private static final int LOCK_ID = 1;

    public void init(Connection conn, DatabaseDialect dialect) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(dialect.createLockTableSql());
        }
        ensureRow(conn, dialect);
    }

    public void acquire(Connection conn, DatabaseDialect dialect) throws SQLException {
        init(conn, dialect);
        String lockedBy = resolveLockedBy();
        String sql = """
                UPDATE schema_changelog_lock
                SET locked = %s, locked_by = ?, locked_at = ?
                WHERE id = ? AND locked = %s
                """.formatted(dialect.lockedTrueLiteral(), dialect.lockedFalseLiteral());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lockedBy);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(3, LOCK_ID);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                String holder = currentHolder(conn);
                throw new SQLException("Could not acquire migration lock"
                        + (holder != null ? "; currently held by " + holder : ""));
            }
        }
        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    public void release(Connection conn, DatabaseDialect dialect) throws SQLException {
        String sql = """
                UPDATE schema_changelog_lock
                SET locked = %s, locked_by = NULL, locked_at = NULL
                WHERE id = ?
                """.formatted(dialect.lockedFalseLiteral());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, LOCK_ID);
            ps.executeUpdate();
        }
        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    private void ensureRow(Connection conn, DatabaseDialect dialect) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement(
                "SELECT id FROM schema_changelog_lock WHERE id = ?")) {
            check.setInt(1, LOCK_ID);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        String insert = """
                INSERT INTO schema_changelog_lock (id, locked, locked_by, locked_at)
                VALUES (?, %s, NULL, NULL)
                """.formatted(dialect.lockedFalseLiteral());
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setInt(1, LOCK_ID);
            ps.executeUpdate();
        }
    }

    private String currentHolder(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT locked_by FROM schema_changelog_lock WHERE id = ?")) {
            ps.setInt(1, LOCK_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private String resolveLockedBy() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        return host + ":" + ProcessHandle.current().pid();
    }
}
