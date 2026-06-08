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

public class MigrationLoader {

    private static final Pattern MIGRATION_PATTERN = Pattern.compile("^V(\\d+)__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLLBACK_PATTERN = Pattern.compile("^U(\\d+)__(.+)\\.sql$", Pattern.CASE_INSENSITIVE);

    public List<MigrationFile> loadMigrations(Path dir) throws IOException {
        return loadScripts(dir, MigrationFile.ScriptType.MIGRATION, MIGRATION_PATTERN);
    }

    public List<MigrationFile> loadRollbacks(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        return loadScripts(dir, MigrationFile.ScriptType.ROLLBACK, ROLLBACK_PATTERN);
    }

    private List<MigrationFile> loadScripts(Path dir, MigrationFile.ScriptType type, Pattern pattern) throws IOException {
        if (!Files.exists(dir)) {
            throw new IOException("Directory not found: " + dir.toAbsolutePath());
        }
        List<MigrationFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        Matcher matcher = pattern.matcher(filename);
                        if (!matcher.matches()) {
                            return;
                        }
                        try {
                            String sql = Files.readString(path);
                            String versionPrefix = type == MigrationFile.ScriptType.MIGRATION ? "V" : "U";
                            String version = versionPrefix + matcher.group(1);
                            String description = matcher.group(2).replace('_', ' ');
                            files.add(new MigrationFile(
                                    version,
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
        files.sort(Comparator.comparingInt(MigrationFile::versionNumber));
        return files;
    }

    public MigrationFile findMigration(List<MigrationFile> migrations, String version) {
        return migrations.stream()
                .filter(m -> m.getVersion().equalsIgnoreCase(version))
                .findFirst()
                .orElse(null);
    }

    public MigrationFile findRollback(List<MigrationFile> rollbacks, String migrationVersion) {
        String rollbackVersion = migrationVersion.replaceFirst("^V", "U");
        return rollbacks.stream()
                .filter(r -> r.getVersion().equalsIgnoreCase(rollbackVersion))
                .findFirst()
                .orElse(null);
    }
}
