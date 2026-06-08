package com.dbshift.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.dbshift.config.DbshiftConfig;
import com.dbshift.model.MigrationFile;
import com.dbshift.model.ValidationResult;

public class MigrationValidator {

    private static final Pattern MIGRATION_FILENAME = Pattern.compile("^V\\d+__.+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLLBACK_FILENAME = Pattern.compile("^U\\d+__.+\\.sql$", Pattern.CASE_INSENSITIVE);

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

        validateFilenames(migrationsDir, MIGRATION_FILENAME, "migration", result);

        if (Files.exists(rollbackDir)) {
            validateFilenames(rollbackDir, ROLLBACK_FILENAME, "rollback", result);
        } else {
            result.addWarning("Rollback directory not found: " + rollbackDir.toAbsolutePath());
        }

        List<MigrationFile> migrations = migrationLoader.loadMigrations(migrationsDir);
        if (migrations.isEmpty()) {
            result.addWarning("No migration scripts found.");
            return result;
        }

        validateSequence(migrations, result);

        List<MigrationFile> rollbacks = migrationLoader.loadRollbacks(rollbackDir);
        for (MigrationFile migration : migrations) {
            MigrationFile rollback = migrationLoader.findRollback(rollbacks, migration.getVersion());
            if (rollback == null) {
                result.addWarning("Missing rollback script for " + migration.getVersion());
            }
        }

        return result;
    }

    public ValidationResult validateChecksums(DbshiftConfig config, List<com.dbshift.model.ChangelogEntry> applied) throws IOException {
        ValidationResult result = new ValidationResult();
        result.setValid(true);

        List<MigrationFile> migrations = migrationLoader.loadMigrations(Path.of(config.getMigrations().getDir()));
        for (com.dbshift.model.ChangelogEntry entry : applied) {
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

    private void validateFilenames(Path dir, Pattern pattern, String label, ValidationResult result) throws IOException {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".sql")).forEach(path -> {
                String name = path.getFileName().toString();
                if (!pattern.matcher(name).matches()) {
                    result.addError("Invalid " + label + " filename: " + name
                            + " (expected e.g. V001__description.sql or U001__description.sql)");
                }
            });
        }
    }

    private void validateSequence(List<MigrationFile> migrations, ValidationResult result) {
        Set<Integer> seen = new HashSet<>();
        int expected = migrations.get(0).versionNumber();
        for (MigrationFile migration : migrations) {
            int num = migration.versionNumber();
            if (seen.contains(num)) {
                result.addError("Duplicate migration version number: V" + num);
            }
            seen.add(num);
            if (num != expected) {
                result.addError("Migration sequence gap or disorder: expected V"
                        + String.format("%03d", expected) + " but found " + migration.getVersion());
            }
            expected = num + 1;
        }
    }
}
