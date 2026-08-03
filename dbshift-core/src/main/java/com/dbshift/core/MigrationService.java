package com.dbshift.core;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.dbshift.config.DbshiftConfig;
import com.dbshift.config.EnvironmentConfig;
import com.dbshift.dialect.DatabaseDialect;
import com.dbshift.dialect.DialectResolver;
import com.dbshift.model.ChangelogEntry;
import com.dbshift.model.EnvironmentDiff;
import com.dbshift.model.MigrateResult;
import com.dbshift.model.MigrationFile;
import com.dbshift.model.MigrationPlan;
import com.dbshift.model.MigrationStatus;
import com.dbshift.model.RepairResult;
import com.dbshift.model.RollbackPlan;
import com.dbshift.model.ValidationResult;
import com.dbshift.model.Version;

public class MigrationService {

    private final ConnectionFactory connectionFactory;
    private final ChangelogRepository changelogRepository;
    private final MigrationLoader migrationLoader;
    private final MigrationValidator migrationValidator;
    private final MigrationExecutor migrationExecutor;
    private final SqlImpactAnalyzer sqlImpactAnalyzer;
    private final SchemaDiffService schemaDiffService;
    private final MigrationLock migrationLock;
    private final PlaceholderResolver placeholderResolver;

    public MigrationService(ConnectionFactory connectionFactory,
                            ChangelogRepository changelogRepository,
                            MigrationLoader migrationLoader,
                            MigrationValidator migrationValidator,
                            MigrationExecutor migrationExecutor,
                            SqlImpactAnalyzer sqlImpactAnalyzer,
                            SchemaDiffService schemaDiffService,
                            MigrationLock migrationLock,
                            PlaceholderResolver placeholderResolver) {
        this.connectionFactory = connectionFactory;
        this.changelogRepository = changelogRepository;
        this.migrationLoader = migrationLoader;
        this.migrationValidator = migrationValidator;
        this.migrationExecutor = migrationExecutor;
        this.sqlImpactAnalyzer = sqlImpactAnalyzer;
        this.schemaDiffService = schemaDiffService;
        this.migrationLock = migrationLock;
        this.placeholderResolver = placeholderResolver;
    }

    public void init(String envName, DbshiftConfig config) throws SQLException {
        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        try (Connection conn = connectionFactory.open(env)) {
            changelogRepository.initTable(conn, dialect);
            migrationLock.init(conn, dialect);
        }
    }

    public void baseline(String envName, DbshiftConfig config, String baselineVersion, String appliedBy)
            throws SQLException {
        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        Version version = normalizeBaselineVersion(baselineVersion);
        try (Connection conn = connectionFactory.open(env)) {
            if (!changelogRepository.tableExists(conn, dialect, env)) {
                changelogRepository.initTable(conn, dialect);
                migrationLock.init(conn, dialect);
            }
            Optional<ChangelogEntry> existing = changelogRepository.findByVersion(conn, version.display());
            if (existing.isPresent()) {
                throw new IllegalStateException("Baseline version already present: " + version.display());
            }
            if (changelogRepository.baselineVersion(conn).isPresent()) {
                throw new IllegalStateException("Database already baselined");
            }
            List<ChangelogEntry> applied = changelogRepository.findAllApplied(conn, dialect);
            if (!applied.isEmpty()) {
                throw new IllegalStateException("Cannot baseline: migrations already applied");
            }
            changelogRepository.insertBaseline(conn, version, appliedBy == null ? "baseline" : appliedBy, dialect);
        }
    }

    public MigrationStatus status(String envName, DbshiftConfig config) throws IOException, SQLException {
        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        List<MigrationFile> allMigrations = migrationLoader.loadMigrations(Path.of(config.getMigrations().getDir()));
        List<MigrationFile> rollbacks = migrationLoader.loadRollbacks(Path.of(config.getRollback().getDir()));

        MigrationStatus status = new MigrationStatus();
        status.setEnvironment(envName);

        try (Connection conn = connectionFactory.open(env)) {
            if (!changelogRepository.tableExists(conn, dialect, env)) {
                status.setPending(allMigrations);
                return status;
            }
            List<ChangelogEntry> applied = changelogRepository.findAllApplied(conn, dialect);
            status.setApplied(applied);
            Optional<String> latest = changelogRepository.latestAppliedVersion(conn, dialect);
            latest.ifPresent(status::setLatestAppliedVersion);

            Set<String> appliedKeys = appliedVersions(applied);
            Optional<Version> baseline = changelogRepository.baselineVersion(conn);

            List<MigrationFile> pending = allMigrations.stream()
                    .filter(m -> !isApplied(m, appliedKeys))
                    .filter(m -> !isAtOrBelowBaseline(m, baseline))
                    .collect(Collectors.toList());
            status.setPending(pending);

            for (ChangelogEntry entry : applied) {
                if ("BASELINE".equalsIgnoreCase(entry.getScriptType())) {
                    continue;
                }
                if (migrationLoader.findRollback(rollbacks, entry.getVersion()) == null) {
                    status.getMissingRollbackScripts().add(entry.getVersion());
                }
            }
        }
        return status;
    }

    public MigrationPlan plan(String envName, DbshiftConfig config, String targetVersion, boolean dryRun)
            throws IOException, SQLException {
        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        MigrationStatus status = status(envName, config);
        MigrationPlan plan = new MigrationPlan();
        plan.setEnvironment(envName);
        plan.setCurrentVersion(status.getLatestAppliedVersion());
        plan.setDryRun(dryRun);

        List<MigrationFile> pending = new ArrayList<>(status.getPending());
        if (targetVersion != null && !targetVersion.isBlank()) {
            Version target = Version.parse(targetVersion.startsWith("V") || targetVersion.startsWith("v")
                    ? targetVersion : "V" + targetVersion);
            pending = pending.stream()
                    .filter(m -> m.getParsedVersion().compareTo(target) <= 0)
                    .collect(Collectors.toList());
            plan.setTargetVersion(target.display());
        } else if (!pending.isEmpty()) {
            plan.setTargetVersion(pending.get(pending.size() - 1).getVersion());
        } else {
            plan.setTargetVersion(status.getLatestAppliedVersion());
        }

        List<MigrationFile> repeatables = pendingRepeatables(envName, config);
        pending.addAll(repeatables);
        plan.setPending(pending);

        for (MigrationFile file : pending) {
            String sql = placeholderResolver.apply(file.getSql());
            plan.getImpacts().addAll(sqlImpactAnalyzer.analyzeMigration(sql, dialect));
        }

        if (!status.getMissingRollbackScripts().isEmpty()) {
            plan.getWarnings().add("Missing rollback scripts for: "
                    + String.join(", ", status.getMissingRollbackScripts()));
        }
        if (!repeatables.isEmpty()) {
            plan.getWarnings().add("Includes " + repeatables.size() + " repeatable migration(s).");
        }

        dialect.ddlTransactionWarning().ifPresent(plan.getWarnings()::add);
        plan.getWarnings().addAll(analyzeHighRiskWarnings(plan));
        return plan;
    }

    public MigrateResult migrate(String envName, DbshiftConfig config, String targetVersion,
                                 boolean requireConfirm, boolean confirmed, String appliedBy)
            throws IOException, SQLException {
        if (requireConfirm && !confirmed) {
            return fail("Production migration requires confirmed=true.");
        }

        ValidationResult validation = migrationValidator.validate(config);
        if (!validation.isValid()) {
            return fail(String.join("; ", validation.getErrors()));
        }

        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        MigrateResult aggregate = new MigrateResult();
        aggregate.setSuccess(true);

        try (Connection conn = connectionFactory.open(env)) {
            if (!changelogRepository.tableExists(conn, dialect, env)) {
                if (config.isBaselineOnMigrate()) {
                    changelogRepository.initTable(conn, dialect);
                    migrationLock.init(conn, dialect);
                    changelogRepository.insertBaseline(
                            conn,
                            normalizeBaselineVersion(config.getBaselineVersion()),
                            appliedBy == null ? "baseline" : appliedBy,
                            dialect);
                } else {
                    changelogRepository.initTable(conn, dialect);
                    migrationLock.init(conn, dialect);
                }
            }

            migrationLock.acquire(conn, dialect);
            try {
                List<ChangelogEntry> applied = changelogRepository.findAllApplied(conn, dialect);
                ValidationResult checksumValidation = migrationValidator.validateChecksums(config, applied);
                if (!checksumValidation.isValid()) {
                    return fail(String.join("; ", checksumValidation.getErrors()));
                }

                MigrationPlan plan = plan(envName, config, targetVersion, false);
                if (plan.getPending().isEmpty()) {
                    aggregate.setSuccess(true);
                    return aggregate;
                }

                for (MigrationFile file : plan.getPending()) {
                    MigrateResult single = migrationExecutor.executeMigration(
                            conn, file, appliedBy, changelogRepository, dialect);
                    if (!single.isSuccess()) {
                        aggregate.setSuccess(false);
                        aggregate.setFailedVersion(single.getFailedVersion());
                        aggregate.setErrorMessage(single.getErrorMessage());
                        return aggregate;
                    }
                    aggregate.getExecutedVersions().addAll(single.getExecutedVersions());
                }
            } finally {
                migrationLock.release(conn, dialect);
            }
        }
        return aggregate;
    }

    public RepairResult repair(String envName, DbshiftConfig config) throws IOException, SQLException {
        RepairResult result = new RepairResult();
        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);

        try (Connection conn = connectionFactory.open(env)) {
            if (!changelogRepository.tableExists(conn, dialect, env)) {
                result.setSuccess(false);
                result.setErrorMessage("Changelog table does not exist");
                return result;
            }
            migrationLock.acquire(conn, dialect);
            try {
                List<MigrationFile> migrations = migrationLoader.loadMigrations(
                        Path.of(config.getMigrations().getDir()));
                List<MigrationFile> repeatables = migrationLoader.loadRepeatables(
                        Path.of(config.getMigrations().getDir()));

                List<ChangelogEntry> applied = changelogRepository.findAllApplied(conn, dialect);
                for (ChangelogEntry entry : applied) {
                    if ("BASELINE".equalsIgnoreCase(entry.getScriptType())) {
                        continue;
                    }
                    MigrationFile file = migrationLoader.findMigration(migrations, entry.getVersion());
                    if (file != null && !file.getChecksum().equals(entry.getChecksum())) {
                        changelogRepository.updateChecksum(conn, entry.getVersion(), file.getChecksum());
                        result.getRepairedChecksums().add(entry.getVersion());
                    }
                }

                for (ChangelogEntry entry : changelogRepository.findRepeatables(conn, dialect)) {
                    MigrationFile file = migrationLoader.findRepeatable(repeatables, entry.getVersion());
                    if (file != null && !file.getChecksum().equals(entry.getChecksum())) {
                        changelogRepository.updateChecksum(conn, entry.getVersion(), file.getChecksum());
                        result.getRepairedChecksums().add(entry.getVersion());
                    }
                }

                for (ChangelogEntry failed : safeFindFailed(conn)) {
                    changelogRepository.deleteVersion(conn, failed.getVersion());
                    result.getRemovedFailed().add(failed.getVersion());
                }
            } finally {
                migrationLock.release(conn, dialect);
            }
        }
        return result;
    }

    public RollbackPlan rollbackPlan(String envName, DbshiftConfig config, int steps, boolean dryRun)
            throws IOException, SQLException {
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }

        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        MigrationStatus status = status(envName, config);
        List<ChangelogEntry> applied = new ArrayList<>(status.getApplied());
        applied.removeIf(e -> "BASELINE".equalsIgnoreCase(e.getScriptType()));
        if (applied.isEmpty()) {
            throw new IllegalStateException("No migrations applied in " + envName);
        }

        applied.sort(Comparator.comparing(e -> Version.parse(e.getVersion())));
        int startIndex = Math.max(0, applied.size() - steps);
        List<ChangelogEntry> toRollback = new ArrayList<>(applied.subList(startIndex, applied.size()));
        java.util.Collections.reverse(toRollback);

        List<MigrationFile> rollbacks = migrationLoader.loadRollbacks(Path.of(config.getRollback().getDir()));
        RollbackPlan plan = new RollbackPlan();
        plan.setEnvironment(envName);
        plan.setCurrentVersion(status.getLatestAppliedVersion());
        plan.setSteps(steps);
        plan.setDryRun(dryRun);

        for (ChangelogEntry entry : toRollback) {
            MigrationFile rollback = migrationLoader.findRollback(rollbacks, entry.getVersion());
            if (rollback == null) {
                plan.getWarnings().add("Missing rollback script for " + entry.getVersion());
                continue;
            }
            plan.getRollbackScripts().add(rollback);
            plan.getImpacts().addAll(sqlImpactAnalyzer.analyzeMigration(
                    placeholderResolver.apply(rollback.getSql()), dialect));
        }

        if (!toRollback.isEmpty()) {
            int targetIndex = applied.size() - steps - 1;
            if (targetIndex < 0) {
                plan.setTargetVersion(null);
            } else {
                plan.setTargetVersion(applied.get(targetIndex).getVersion());
            }
        }

        plan.getWarnings().add("Rollback removes changelog records only; ensure U scripts are correct.");
        dialect.ddlTransactionWarning().ifPresent(plan.getWarnings()::add);
        return plan;
    }

    public MigrateResult rollback(String envName, DbshiftConfig config, int steps, boolean dryRun,
                                  boolean confirmed, String appliedBy) throws IOException, SQLException {
        RollbackPlan plan = rollbackPlan(envName, config, steps, dryRun);
        if (dryRun) {
            MigrateResult preview = new MigrateResult();
            preview.setSuccess(true);
            return preview;
        }
        if (!confirmed) {
            return fail("Rollback execution requires confirmed=true.");
        }
        if (!plan.getWarnings().stream().filter(w -> w.startsWith("Missing rollback")).toList().isEmpty()) {
            return fail("Cannot rollback: missing rollback scripts.");
        }

        MigrationStatus status = status(envName, config);
        List<ChangelogEntry> applied = new ArrayList<>(status.getApplied());
        applied.removeIf(e -> "BASELINE".equalsIgnoreCase(e.getScriptType()));
        applied.sort(Comparator.comparing(e -> Version.parse(e.getVersion())));
        int startIndex = Math.max(0, applied.size() - steps);
        List<ChangelogEntry> toRollback = new ArrayList<>(applied.subList(startIndex, applied.size()));
        java.util.Collections.reverse(toRollback);

        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        MigrateResult aggregate = new MigrateResult();
        aggregate.setSuccess(true);

        try (Connection conn = connectionFactory.open(env)) {
            migrationLock.acquire(conn, dialect);
            try {
                for (ChangelogEntry entry : toRollback) {
                    MigrationFile rollback = migrationLoader.findRollback(
                            migrationLoader.loadRollbacks(Path.of(config.getRollback().getDir())),
                            entry.getVersion());
                    MigrateResult single = migrationExecutor.executeRollback(
                            conn, rollback, entry.getVersion(), appliedBy, changelogRepository);
                    if (!single.isSuccess()) {
                        aggregate.setSuccess(false);
                        aggregate.setFailedVersion(single.getFailedVersion());
                        aggregate.setErrorMessage(single.getErrorMessage());
                        return aggregate;
                    }
                    aggregate.getExecutedVersions().addAll(single.getExecutedVersions());
                }
            } finally {
                migrationLock.release(conn, dialect);
            }
        }
        return aggregate;
    }

    public ValidationResult validate(DbshiftConfig config) throws IOException {
        return migrationValidator.validate(config);
    }

    public EnvironmentDiff diffEnvironments(DbshiftConfig config, boolean includeSchemaDiff,
                                            String schemaFrom, String schemaTo) throws IOException, SQLException {
        EnvironmentDiff diff = new EnvironmentDiff();
        for (String envName : config.getEnvironments().keySet()) {
            MigrationStatus status = status(envName, config);
            EnvironmentDiff.EnvironmentVersionSummary summary = new EnvironmentDiff.EnvironmentVersionSummary();
            summary.setName(envName);
            summary.setAppliedVersion(status.getLatestAppliedVersion());
            List<String> pending = status.getPending().stream()
                    .map(MigrationFile::getVersion)
                    .collect(Collectors.toList());
            summary.setPendingVersions(pending);
            diff.getEnvironments().add(summary);
            diff.getPendingByEnvironment().put(envName, pending);
        }

        if (includeSchemaDiff && schemaFrom != null && schemaTo != null) {
            diff.setSchemaDifferences(schemaDiffService.compare(
                    config.requireEnvironment(schemaFrom),
                    config.requireEnvironment(schemaTo),
                    schemaFrom,
                    schemaTo
            ));
        }
        return diff;
    }

    private List<MigrationFile> pendingRepeatables(String envName, DbshiftConfig config)
            throws IOException, SQLException {
        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        List<MigrationFile> repeatables = migrationLoader.loadRepeatables(
                Path.of(config.getMigrations().getDir()));
        if (repeatables.isEmpty()) {
            return List.of();
        }
        try (Connection conn = connectionFactory.open(env)) {
            if (!changelogRepository.tableExists(conn, dialect, env)) {
                return repeatables;
            }
            Map<String, String> appliedChecksums = new HashMap<>();
            for (ChangelogEntry entry : changelogRepository.findRepeatables(conn, dialect)) {
                appliedChecksums.put(entry.getVersion(), entry.getChecksum());
            }
            return repeatables.stream()
                    .filter(r -> !r.getChecksum().equals(appliedChecksums.get(r.getVersion())))
                    .collect(Collectors.toList());
        }
    }

    private Set<String> appliedVersions(List<ChangelogEntry> applied) {
        Set<String> keys = new HashSet<>();
        for (ChangelogEntry entry : applied) {
            try {
                keys.add(Version.parse(entry.getVersion()).display());
            } catch (Exception e) {
                keys.add(entry.getVersion());
            }
        }
        return keys;
    }

    private boolean isApplied(MigrationFile file, Set<String> appliedKeys) {
        return appliedKeys.contains(file.getParsedVersion().display())
                || appliedKeys.contains(file.getVersion());
    }

    private boolean isAtOrBelowBaseline(MigrationFile file, Optional<Version> baseline) {
        return baseline.isPresent() && file.getParsedVersion().compareTo(baseline.get()) <= 0;
    }

    private Version normalizeBaselineVersion(String baselineVersion) {
        if (baselineVersion == null || baselineVersion.isBlank()) {
            return Version.parse("V1");
        }
        String v = baselineVersion.trim();
        if (!v.toUpperCase().startsWith("V") && !v.toUpperCase().startsWith("U")) {
            v = "V" + v;
        }
        return Version.parse(v);
    }

    private List<ChangelogEntry> safeFindFailed(Connection conn) throws SQLException {
        try {
            return changelogRepository.findFailed(conn);
        } catch (SQLException e) {
            return List.of();
        }
    }

    private MigrateResult fail(String message) {
        MigrateResult failed = new MigrateResult();
        failed.setSuccess(false);
        failed.setErrorMessage(message);
        return failed;
    }

    private List<String> analyzeHighRiskWarnings(MigrationPlan plan) {
        List<String> warnings = new ArrayList<>();
        long highRisk = plan.getImpacts().stream()
                .filter(i -> i.getRiskLevel() == com.dbshift.model.SqlImpact.RiskLevel.HIGH)
                .count();
        if (highRisk > 0) {
            warnings.add("Plan contains " + highRisk + " high-risk SQL statement(s).");
        }
        return warnings;
    }
}
