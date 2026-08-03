package com.dbshift.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.dbshift.model.MigrationFile;
import com.dbshift.model.Version;

public class MigrationLoader {

    private static final Pattern VERSIONED_PATTERN =
            Pattern.compile("^([VU])(\\d+(?:[._]\\d+)*)__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATABLE_PATTERN =
            Pattern.compile("^R__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);

    public List<MigrationFile> loadMigrations(Path dir) throws IOException {
        return loadVersioned(dir, MigrationFile.ScriptType.MIGRATION, 'V');
    }

    public List<MigrationFile> loadRollbacks(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        return loadVersioned(dir, MigrationFile.ScriptType.ROLLBACK, 'U');
    }

    public List<MigrationFile> loadRepeatables(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        List<MigrationFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        Matcher matcher = REPEATABLE_PATTERN.matcher(filename);
                        if (!matcher.matches()) {
                            return;
                        }
                        try {
                            String sql = Files.readString(path);
                            String description = matcher.group(1).replace('_', ' ');
                            String versionKey = "R__" + matcher.group(1);
                            files.add(new MigrationFile(
                                    versionKey,
                                    description,
                                    path,
                                    ChecksumUtil.sha256(sql),
                                    MigrationFile.ScriptType.REPEATABLE,
                                    sql
                            ));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read " + path, e);
                        }
                    });
        }
        return files;
    }

    private List<MigrationFile> loadVersioned(Path dir, MigrationFile.ScriptType type, char expectedPrefix)
            throws IOException {
        if (!Files.exists(dir)) {
            throw new IOException("Directory not found: " + dir.toAbsolutePath());
        }
        List<MigrationFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        Matcher matcher = VERSIONED_PATTERN.matcher(filename);
                        if (!matcher.matches()) {
                            return;
                        }
                        char prefix = Character.toUpperCase(matcher.group(1).charAt(0));
                        if (prefix != expectedPrefix) {
                            return;
                        }
                        try {
                            String sql = Files.readString(path);
                            Version version = Version.parse(prefix + matcher.group(2));
                            String description = matcher.group(3).replace('_', ' ');
                            files.add(new MigrationFile(
                                    version.display(),
                                    description,
                                    path,
                                    ChecksumUtil.sha256(sql),
                                    type,
                                    sql
                            ));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read " + path, e);
                        }
                    });
        }
        files.sort(Comparator.comparing(MigrationFile::getParsedVersion));
        return files;
    }

    public MigrationFile findMigration(List<MigrationFile> migrations, String version) {
        Version target = Version.parse(version);
        return migrations.stream()
                .filter(m -> m.getParsedVersion() != null && m.getParsedVersion().equals(target))
                .findFirst()
                .orElseGet(() -> migrations.stream()
                        .filter(m -> m.getVersion().equalsIgnoreCase(version))
                        .findFirst()
                        .orElse(null));
    }

    public MigrationFile findRollback(List<MigrationFile> rollbacks, String migrationVersion) {
        Version migration = Version.parse(migrationVersion);
        String rollbackDisplay = "U" + migration.display().substring(1);
        return rollbacks.stream()
                .filter(r -> r.getParsedVersion() != null
                        && r.getParsedVersion().equals(Version.parse(rollbackDisplay)))
                .findFirst()
                .orElse(null);
    }

    public MigrationFile findRepeatable(List<MigrationFile> repeatables, String versionKey) {
        return repeatables.stream()
                .filter(r -> r.getVersion().equalsIgnoreCase(versionKey))
                .findFirst()
                .orElse(null);
    }
}
