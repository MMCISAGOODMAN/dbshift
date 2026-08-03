package com.dbshift.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.dbshift.config.DbshiftConfig;
import com.dbshift.model.ChangelogEntry;
import com.dbshift.model.MigrationFile;
import com.dbshift.model.ValidationResult;
import com.dbshift.model.Version;

public class MigrationValidator {

    private static final Pattern VERSIONED_FILENAME =
            Pattern.compile("^[VU]\\d+(?:[._]\\d+)*__.+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATABLE_FILENAME =
            Pattern.compile("^R__.+\\.sql$", Pattern.CASE_INSENSITIVE);

    private final MigrationLoader migrationLoader;

    public MigrationValidator(MigrationLoader migrationLoader) {
        this.migrationLoader = migrationLoader;
    }

    public ValidationResult validate(DbshiftConfig config) throws IOException {
        ValidationResult result = new ValidationResult();
        result.setValid(true);

        Path migrationsDir = Path.of(config.getMigrations().getDir());
        Path rollbackDir = Path.of(config.getRollback().getDir());

        if (!Files.exists(migrationsDir)) {
            result.addError("Migrations directory not found: " + migrationsDir.toAbsolutePath());
            return result;
        }

        validateMigrationDirFilenames(migrationsDir, result);

        if (Files.exists(rollbackDir)) {
            validateRollbackFilenames(rollbackDir, result);
        } else {
            result.addWarning("Rollback directory not found: " + rollbackDir.toAbsolutePath());
        }

        List<MigrationFile> migrations = migrationLoader.loadMigrations(migrationsDir);
        if (migrations.isEmpty()) {
            result.addWarning("No versioned migration scripts found.");
        } else {
            validateUniqueness(migrations, result);
        }

        List<MigrationFile> rollbacks = migrationLoader.loadRollbacks(rollbackDir);
        for (MigrationFile migration : migrations) {
            MigrationFile rollback = migrationLoader.findRollback(rollbacks, migration.getVersion());
            if (rollback == null) {
                result.addWarning("Missing rollback script for " + migration.getVersion());
            }
        }

        return result;
    }

    public ValidationResult validateChecksums(DbshiftConfig config, List<ChangelogEntry> applied) throws IOException {
        ValidationResult result = new ValidationResult();
        result.setValid(true);

        List<MigrationFile> migrations = migrationLoader.loadMigrations(Path.of(config.getMigrations().getDir()));
        for (ChangelogEntry entry : applied) {
            if ("BASELINE".equalsIgnoreCase(entry.getScriptType())) {
                continue;
            }
            MigrationFile file = migrationLoader.findMigration(migrations, entry.getVersion());
            if (file == null) {
                result.addError("Applied version " + entry.getVersion() + " not found in migration files.");
                continue;
            }
            if (!file.getChecksum().equals(entry.getChecksum())) {
                result.addError("Checksum mismatch for " + entry.getVersion()
                        + ". The migration file was modified after being applied.");
            }
        }
        return result;
    }

    private void validateMigrationDirFilenames(Path dir, ValidationResult result) throws IOException {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".sql")).forEach(path -> {
                String name = path.getFileName().toString();
                boolean ok = (VERSIONED_FILENAME.matcher(name).matches()
                        && name.toUpperCase().startsWith("V"))
                        || REPEATABLE_FILENAME.matcher(name).matches();
                if (!ok) {
                    result.addError("Invalid migration filename: " + name
                            + " (expected V1__desc.sql, V1_2__desc.sql or R__desc.sql)");
                }
            });
        }
    }

    private void validateRollbackFilenames(Path dir, ValidationResult result) throws IOException {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".sql")).forEach(path -> {
                String name = path.getFileName().toString();
                if (!VERSIONED_FILENAME.matcher(name).matches() || !name.toUpperCase().startsWith("U")) {
                    result.addError("Invalid rollback filename: " + name
                            + " (expected U1__desc.sql or U1_2__desc.sql)");
                }
            });
        }
    }

    private void validateUniqueness(List<MigrationFile> migrations, ValidationResult result) {
        Set<Version> seen = new HashSet<>();
        for (MigrationFile migration : migrations) {
            Version version = migration.getParsedVersion();
            if (!seen.add(version)) {
                result.addError("Duplicate migration version: " + migration.getVersion());
            }
        }
    }
}
