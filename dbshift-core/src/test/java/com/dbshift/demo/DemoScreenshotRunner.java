package com.dbshift.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.dbshift.DbshiftEngine;
import com.dbshift.config.EnvironmentConfig;
import com.dbshift.config.ConfigLoader;

/**
 * Generates demo outputs and database dumps for WeChat article screenshots.
 * Run: mvn -pl dbshift-core test -Dtest=DemoScreenshotRunner
 */
class DemoScreenshotRunner {

    private static final Path OUTPUT = Path.of("../docs/demo/output");
    private static final Path CONFIG = Path.of("../docs/demo/dbshift-demo.yaml");

    @org.junit.jupiter.api.Test
    void generateDemoArtifacts() throws Exception {
        Files.createDirectories(OUTPUT);
        Files.createDirectories(Path.of("../docs/demo/data"));

        DbshiftEngine engine = DbshiftEngine.fromYaml(CONFIG);

        StringBuilder console = new StringBuilder();
        append(console, "=== DbShift Demo Console ===\n");
        append(console, "$ engine.validate()\n");
        var validation = engine.validate();
        append(console, "Valid: " + validation.isValid() + "\n\n");

        append(console, "$ engine.init(\"dev\")\n");
        engine.init("dev");
        append(console, "schema_changelog table created.\n\n");

        append(console, "$ engine.status(\"dev\")  [before migrate]\n");
        var statusBefore = engine.status("dev");
        append(console, formatStatus(statusBefore));

        append(console, "$ engine.plan(\"dev\", null, true)\n");
        var plan = engine.plan("dev", null, true);
        Files.writeString(OUTPUT.resolve("migration-plan.md"), engine.reports().toMarkdownPlan(plan));
        Files.writeString(OUTPUT.resolve("migration-plan.html"), engine.reports().toHtmlPlan(plan));
        append(console, "Plan: " + plan.getPending().size() + " pending migrations → "
                + plan.getTargetVersion() + "\n");
        append(console, "Reports saved: migration-plan.md / migration-plan.html\n\n");

        append(console, "$ engine.migrate(\"dev\", null, false, true, \"demo-app\")\n");
        var result = engine.migrate("dev", null, false, true, "demo-app");
        append(console, "Success: " + result.isSuccess() + "\n");
        append(console, "Applied: " + String.join(", ", result.getExecutedVersions()) + "\n\n");

        seedBusinessData(engine);

        append(console, "$ engine.status(\"dev\")  [after migrate]\n");
        var statusAfter = engine.status("dev");
        append(console, formatStatus(statusAfter));

        Files.writeString(OUTPUT.resolve("console-output.txt"), console.toString());

        EnvironmentConfig env = engine.config().requireEnvironment("dev");
        dumpTable(env, "schema_changelog", OUTPUT.resolve("db-schema_changelog.txt"));
        dumpTable(env, "users", OUTPUT.resolve("db-users.txt"));
        dumpTable(env, "orders", OUTPUT.resolve("db-orders.txt"));
        dumpAllTables(env, OUTPUT.resolve("db-all-tables.txt"));
    }

    private void seedBusinessData(DbshiftEngine engine) throws SQLException {
        EnvironmentConfig env = engine.config().requireEnvironment("dev");
        try (Connection conn = DriverManager.getConnection(env.getUrl(), env.getUser(), env.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO users (username, email) VALUES
                    ('alice', 'alice@example.com'),
                    ('bob', 'bob@example.com'),
                    ('charlie', 'charlie@example.com')
                    """);
            stmt.execute("""
                    INSERT INTO orders (user_id, status, amount) VALUES
                    (1, 'PAID', 199.00),
                    (1, 'PENDING', 49.50),
                    (2, 'PAID', 320.00)
                    """);
        }
    }

    private String formatStatus(com.dbshift.model.MigrationStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append("Environment: ").append(status.getEnvironment()).append("\n");
        sb.append("Latest applied: ").append(status.getLatestAppliedVersion()).append("\n");
        sb.append("Applied count: ").append(status.getApplied().size()).append("\n");
        sb.append("Pending count: ").append(status.getPending().size()).append("\n");
        if (!status.getApplied().isEmpty()) {
            sb.append("Applied versions:\n");
            status.getApplied().forEach(e ->
                    sb.append("  - ").append(e.getVersion())
                            .append(" (").append(e.getDescription()).append(")\n"));
        }
        if (!status.getPending().isEmpty()) {
            sb.append("Pending:\n");
            status.getPending().forEach(m ->
                    sb.append("  - ").append(m.getVersion()).append("\n"));
        }
        sb.append("\n");
        return sb.toString();
    }

    private void dumpTable(EnvironmentConfig env, String table, Path out) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("TABLE: ").append(table).append("\n");
        sb.append(repeat("=", 72)).append("\n");
        try (Connection conn = DriverManager.getConnection(env.getUrl(), env.getUser(), env.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
            sb.append(formatResultSet(rs));
        }
        Files.writeString(out, sb.toString());
    }

    private void dumpAllTables(EnvironmentConfig env, Path out) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Database tables after migration + seed data\n");
        sb.append(repeat("=", 72)).append("\n\n");
        List<String> tables = List.of("schema_changelog", "users", "orders");
        for (String table : tables) {
            try (Connection conn = DriverManager.getConnection(env.getUrl(), env.getUser(), env.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                sb.append(">>> ").append(table).append("\n");
                sb.append(formatResultSet(rs)).append("\n");
            }
        }
        Files.writeString(out, sb.toString());
    }

    private String formatResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        List<String> headers = new ArrayList<>();
        for (int i = 1; i <= cols; i++) {
            headers.add(meta.getColumnLabel(i));
        }

        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= cols; i++) {
                String val = rs.getString(i);
                row.add(val == null ? "NULL" : val);
            }
            rows.add(row);
        }

        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = headers.get(i).length();
            for (List<String> row : rows) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
            widths[i] = Math.min(widths[i], 24);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(formatRow(headers, widths)).append("\n");
        sb.append(formatSep(widths)).append("\n");
        for (List<String> row : rows) {
            sb.append(formatRow(row, widths)).append("\n");
        }
        if (rows.isEmpty()) {
            sb.append("(empty)\n");
        }
        return sb.toString();
    }

    private String formatRow(List<String> cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            if (cell.length() > widths[i]) {
                cell = cell.substring(0, widths[i]);
            }
            sb.append(String.format("%-" + widths[i] + "s  ", cell));
        }
        return sb.toString().stripTrailing();
    }

    private String formatSep(int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int w : widths) {
            sb.append(repeat("-", w)).append("--");
        }
        return sb.toString();
    }

    private String repeat(String s, int n) {
        return s.repeat(n);
    }

    private void append(StringBuilder sb, String text) {
        sb.append(text);
        System.out.print(text);
    }
}
