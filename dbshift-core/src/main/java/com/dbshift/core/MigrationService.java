package com.dbshift.core;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
import com.dbshift.model.RollbackPlan;
import com.dbshift.model.ValidationResult;

public class MigrationService {

    private final ConnectionFactory connectionFactory;
    private final ChangelogRepository changelogRepository;
    private final MigrationLoader migrationLoader;
    private final MigrationValidator migrationValidator;
    private final MigrationExecutor migrationExecutor;
    private final SqlImpactAnalyzer sqlImpactAnalyzer;
    private final SchemaDiffService schemaDiffService;

    public MigrationService(ConnectionFactory connectionFactory,
                            ChangelogRepository changelogRepository,
                            MigrationLoader migrationLoader,
                            MigrationValidator migrationValidator,
                            MigrationExecutor migrationExecutor,
                            SqlImpactAnalyzer sqlImpactAnalyzer,
                            SchemaDiffService schemaDiffService) {
        this.connectionFactory = connectionFactory;
        this.changelogRepository = changelogRepository;
        this.migrationLoader = migrationLoader;
        this.migrationValidator = migrationValidator;
        this.migrationExecutor = migrationExecutor;
        this.sqlImpactAnalyzer = sqlImpactAnalyzer;
        this.schemaDiffService = schemaDiffService;
    }

    public void init(String envName, DbshiftConfig config) throws SQLException {
        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        try (Connection conn = connectionFactory.open(env)) {
            changelogRepository.initTable(conn, dialect);
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

            Set<String> appliedVersions = applied.stream()
                    .map(ChangelogEntry::getVersion)
                    .collect(Collectors.toCollection(HashSet::new));

            List<MigrationFile> pending = allMigrations.stream()
                    .filter(m -> !appliedVersions.contains(m.getVersion()))
                    .collect(Collectors.toList());
            status.setPending(pending);

            for (ChangelogEntry entry : applied) {
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
            int targetNum = parseVersionNumber(targetVersion);
            pending = pending.stream()
                    .filter(m -> m.versionNumber() <= targetNum)
                    .collect(Collectors.toList());
            plan.setTargetVersion(normalizeVersion(targetVersion));
        } else if (!pending.isEmpty()) {
            plan.setTargetVersion(pending.get(pending.size() - 1).getVersion());
        } else {
            plan.setTargetVersion(status.getLatestAppliedVersion());
        }

        plan.setPending(pending);
        for (MigrationFile file : pending) {
            plan.getImpacts().addAll(sqlImpactAnalyzer.analyzeMigration(file.getSql(), dialect));
        }

        if (!status.getMissingRollbackScripts().isEmpty()) {
            plan.getWarnings().add("Missing rollback scripts for: "
                    + String.join(", ", status.getMissingRollbackScripts()));
        }

        dialect.ddlTransactionWarning().ifPresent(plan.getWarnings()::add);
        plan.getWarnings().addAll(analyzeHighRiskWarnings(plan));

        return plan;
    }

    public MigrateResult migrate(String envName, DbshiftConfig config, String targetVersion,
                                 boolean requireConfirm, boolean confirmed, String appliedBy)
            throws IOException, SQLException {
        if (requireConfirm && !confirmed) {
            MigrateResult blocked = new MigrateResult();
            blocked.setSuccess(false);
            blocked.setErrorMessage("Production migration requires confirmed=true.");
            return blocked;
        }

        ValidationResult validation = migrationValidator.validate(config);
        if (!validation.isValid()) {
            MigrateResult failed = new MigrateResult();
            failed.setSuccess(false);
            failed.setErrorMessage(String.join("; ", validation.getErrors()));
            return failed;
        }

        MigrationPlan plan = plan(envName, config, targetVersion, false);
        if (plan.getPending().isEmpty()) {
            MigrateResult empty = new MigrateResult();
            empty.setSuccess(true);
            return empty;
        }

        EnvironmentConfig env = config.requireEnvironment(envName);
        DatabaseDialect dialect = DialectResolver.resolve(env);
        MigrateResult aggregate = new MigrateResult();
        aggregate.setSuccess(true);

        try (Connection conn = connectionFactory.open(env)) {
            if (!changelogRepository.tableExists(conn, dialect, env)) {
                changelogRepository.initTable(conn, dialect);
            }

            List<ChangelogEntry> applied = changelogRepository.findAllApplied(conn, dialect);
            ValidationResult checksumValidation = migrationValidator.validateChecksums(config, applied);
            if (!checksumValidation.isValid()) {
                aggregate.setSuccess(false);
                aggregate.setErrorMessage(String.join("; ", checksumValidation.getErrors()));
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
        }
        return aggregate;
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
        if (applied.isEmpty()) {
            throw new IllegalStateException("No migrations applied in " + envName);
        }

        applied.sort(Comparator.comparing(ChangelogEntry::getVersion));
        int startIndex = Math.max(0, applied.size() - steps);
        List<ChangelogEntry> toRollback = applied.subList(startIndex, applied.size());
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
            plan.getImpacts().addAll(sqlImpactAnalyzer.analyzeMigration(rollback.getSql(), dialect));
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
            MigrateResult blocked = new MigrateResult();
            blocked.setSuccess(false);
            blocked.setErrorMessage("Rollback execution requires confirmed=true.");
            return blocked;
        }
        if (!plan.getWarnings().stream().filter(w -> w.startsWith("Missing rollback")).toList().isEmpty()) {
            MigrateResult failed = new MigrateResult();
            failed.setSuccess(false);
            failed.setErrorMessage("Cannot rollback: missing rollback scripts.");
            return failed;
        }

        MigrationStatus status = status(envName, config);
        List<ChangelogEntry> applied = new ArrayList<>(status.getApplied());
        applied.sort(Comparator.comparing(ChangelogEntry::getVersion));
        int startIndex = Math.max(0, applied.size() - steps);
        List<ChangelogEntry> toRollback = applied.subList(startIndex, applied.size());
        java.util.Collections.reverse(toRollback);

        EnvironmentConfig env = config.requireEnvironment(envName);
        MigrateResult aggregate = new MigrateResult();
        aggregate.setSuccess(true);

        try (Connection conn = connectionFactory.open(env)) {
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

    private int parseVersionNumber(String version) {
        return Integer.parseInt(version.replaceFirst("(?i)^V", ""));
    }

    private String normalizeVersion(String version) {
        int num = parseVersionNumber(version);
        return "V" + String.format("%03d", num);
    }
}
