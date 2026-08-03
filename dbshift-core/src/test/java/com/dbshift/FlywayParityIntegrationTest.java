package com.dbshift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dbshift.config.DbshiftConfig;
import com.dbshift.config.EnvironmentConfig;
import com.dbshift.model.MigrateResult;
import com.dbshift.model.RepairResult;

class FlywayParityIntegrationTest {

    @TempDir
    Path tempDir;

    private Path migrationsDir;
    private Path rollbackDir;
    private String jdbcUrl;
    private DbshiftEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        migrationsDir = tempDir.resolve("migrations");
        rollbackDir = tempDir.resolve("rollback");
        Files.createDirectories(migrationsDir);
        Files.createDirectories(rollbackDir);
        jdbcUrl = "jdbc:h2:mem:parity_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

        Files.writeString(migrationsDir.resolve("V1__create_users.sql"), """
                CREATE TABLE ${schema}.users (
                    id INT PRIMARY KEY,
                    name VARCHAR(64)
                );
                """);
        Files.writeString(migrationsDir.resolve("V1_1__add_email.sql"), """
                ALTER TABLE ${schema}.users ADD COLUMN email VARCHAR(128);
                """);
        Files.writeString(migrationsDir.resolve("V2__create_orders.sql"), """
                CREATE TABLE ${schema}.orders (
                    id INT PRIMARY KEY,
                    user_id INT
                );
                """);
        Files.writeString(migrationsDir.resolve("R__user_view.sql"), """
                CREATE OR REPLACE VIEW ${schema}.user_view AS SELECT id, name FROM ${schema}.users;
                """);
        Files.writeString(rollbackDir.resolve("U1__drop_users.sql"), "DROP TABLE IF EXISTS app.users;");
        Files.writeString(rollbackDir.resolve("U1_1__drop_email.sql"), "ALTER TABLE app.users DROP COLUMN email;");
        Files.writeString(rollbackDir.resolve("U2__drop_orders.sql"), "DROP TABLE IF EXISTS app.orders;");

        engine = DbshiftEngine.fromConfig(config());
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS app");
        }
    }

    @Test
    void migrateBaselineRepeatableRepairAndRollback() throws Exception {
        assertTrue(engine.validate().isValid());

        engine.init("dev");
        MigrateResult migrate = engine.migrate("dev", null, false, true, "test");
        assertTrue(migrate.isSuccess(), migrate.getErrorMessage());
        assertTrue(migrate.getExecutedVersions().contains("V1"));
        assertTrue(migrate.getExecutedVersions().contains("V1.1"));
        assertTrue(migrate.getExecutedVersions().contains("V2"));
        assertTrue(migrate.getExecutedVersions().stream().anyMatch(v -> v.startsWith("R__")));

        assertEquals("V2", engine.status("dev").getLatestAppliedVersion());
        assertEquals(0, engine.status("dev").getPending().size());

        // change repeatable -> should be pending again
        Files.writeString(migrationsDir.resolve("R__user_view.sql"), """
                CREATE OR REPLACE VIEW ${schema}.user_view AS SELECT id, name, email FROM ${schema}.users;
                """);
        assertEquals(1, engine.plan("dev", null, true).getPending().size());
        assertTrue(engine.migrate("dev", null, false, true, "test").isSuccess());

        // checksum drift repair
        Files.writeString(migrationsDir.resolve("V1__create_users.sql"), """
                CREATE TABLE ${schema}.users (
                    id INT PRIMARY KEY,
                    name VARCHAR(64),
                    note VARCHAR(16)
                );
                """);
        RepairResult repair = engine.repair("dev");
        assertTrue(repair.isSuccess());
        assertTrue(repair.getRepairedChecksums().contains("V1"));

        MigrateResult rollback = engine.rollback("dev", 1, false, true, "test");
        assertTrue(rollback.isSuccess(), rollback.getErrorMessage());
        assertEquals("V1.1", engine.status("dev").getLatestAppliedVersion());
    }

    @Test
    void baselineSkipsHistoricalVersions() throws Exception {
        // Repeatable must not depend on skipped historical tables
        Files.writeString(migrationsDir.resolve("R__user_view.sql"), """
                CREATE OR REPLACE VIEW ${schema}.meta_view AS SELECT 1 AS n;
                """);

        DbshiftEngine baselined = DbshiftEngine.fromConfig(config());
        baselined.baseline("dev", "V1.1", "ops");
        MigrateResult result = baselined.migrate("dev", null, false, true, "test");
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertFalse(result.getExecutedVersions().contains("V1"));
        assertFalse(result.getExecutedVersions().contains("V1.1"));
        assertTrue(result.getExecutedVersions().contains("V2"));
        assertEquals("V2", baselined.status("dev").getLatestAppliedVersion());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='app' AND lower(table_name)='orders'")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    private DbshiftConfig config() {
        DbshiftConfig config = new DbshiftConfig();
        EnvironmentConfig env = new EnvironmentConfig();
        env.setUrl(jdbcUrl);
        env.setUser("sa");
        env.setPassword("");
        env.setDialect("h2");
        Map<String, EnvironmentConfig> envs = new LinkedHashMap<>();
        envs.put("dev", env);
        config.setEnvironments(envs);
        config.getMigrations().setDir(migrationsDir.toString());
        config.getRollback().setDir(rollbackDir.toString());
        config.setPlaceholders(Map.of("schema", "app"));
        return config;
    }
}
