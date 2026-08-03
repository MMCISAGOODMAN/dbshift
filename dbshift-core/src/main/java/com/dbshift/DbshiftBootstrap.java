package com.dbshift;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

import com.dbshift.config.AutoMigrateSettings;
import com.dbshift.model.MigrateResult;
import com.dbshift.model.ValidationResult;

/**
 * Runs pending migrations automatically on application startup when enabled in config.
 *
 * <pre>{@code
 * // Option 1: one line in main()
 * DbshiftBootstrap.start(Path.of("dbshift.yaml"));
 *
 * // Option 2: via engine
 * DbshiftEngine engine = DbshiftEngine.fromYaml(Path.of("dbshift.yaml"));
 * engine.start();
 * }</pre>
 */
public final class DbshiftBootstrap {

    private DbshiftBootstrap() {
    }

    /**
     * Load config and auto-migrate if {@code autoMigrate.enabled=true} in yaml.
     */
    public static void start(Path configFile) {
        try {
            DbshiftEngine engine = DbshiftEngine.fromYaml(configFile);
            start(engine);
        } catch (IOException e) {
            throw new DbshiftException("Failed to load dbshift config: " + configFile, e);
        }
    }

    /**
     * Auto-migrate using settings from engine config. No-op if autoMigrate is disabled.
     */
    public static void start(DbshiftEngine engine) {
        AutoMigrateSettings auto = engine.config().getAutoMigrate();
        if (auto == null || !auto.isEnabled()) {
            return;
        }
        try {
            run(engine, auto);
        } catch (SQLException | IOException e) {
            if (auto.isFailOnError()) {
                throw new DbshiftException("DbShift auto-migrate failed", e);
            }
            System.err.println("[dbshift] auto-migrate failed (failOnError=false): " + e.getMessage());
        }
    }

    /**
     * Force auto-migrate with explicit environment (ignores yaml enabled flag).
     */
    public static void start(DbshiftEngine engine, String environment) {
        AutoMigrateSettings auto = copySettings(engine.config().getAutoMigrate());
        auto.setEnabled(true);
        auto.setEnvironment(environment);
        try {
            run(engine, auto);
        } catch (SQLException | IOException e) {
            if (auto.isFailOnError()) {
                throw new DbshiftException("DbShift auto-migrate failed for env: " + environment, e);
            }
            System.err.println("[dbshift] auto-migrate failed (failOnError=false): " + e.getMessage());
        }
    }

    private static void run(DbshiftEngine engine, AutoMigrateSettings auto)
            throws SQLException, IOException {
        String env = auto.getEnvironment();
        System.out.println("[dbshift] auto-migrate starting for environment: " + env);

        ValidationResult validation = engine.validate();
        if (!validation.isValid()) {
            throw new DbshiftException("Migration validation failed: " + String.join("; ", validation.getErrors()));
        }

        if (auto.isInitOnStartup() && !engine.config().isBaselineOnMigrate()) {
            engine.init(env);
        }

        boolean requireConfirm = auto.isRequireConfirmForProd()
                && ("prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env));
        boolean confirmed = !requireConfirm;

        if (requireConfirm) {
            System.out.println("[dbshift] prod environment requires manual migrate; skipping auto-migrate.");
            return;
        }

        MigrateResult result = engine.migrate(
                env,
                auto.getTargetVersion(),
                requireConfirm,
                confirmed,
                auto.getAppliedBy()
        );

        if (!result.isSuccess()) {
            throw new DbshiftException("Migration failed: " + result.getErrorMessage()
                    + (result.getFailedVersion() != null ? " at " + result.getFailedVersion() : ""));
        }

        if (result.getExecutedVersions().isEmpty()) {
            System.out.println("[dbshift] auto-migrate complete: no pending migrations.");
        } else {
            System.out.println("[dbshift] auto-migrate applied: " + String.join(", ", result.getExecutedVersions()));
        }
    }

    private static AutoMigrateSettings copySettings(AutoMigrateSettings source) {
        AutoMigrateSettings copy = new AutoMigrateSettings();
        if (source == null) {
            return copy;
        }
        copy.setEnabled(source.isEnabled());
        copy.setEnvironment(source.getEnvironment());
        copy.setInitOnStartup(source.isInitOnStartup());
        copy.setFailOnError(source.isFailOnError());
        copy.setAppliedBy(source.getAppliedBy());
        copy.setTargetVersion(source.getTargetVersion());
        copy.setRequireConfirmForProd(source.isRequireConfirmForProd());
        return copy;
    }
}
