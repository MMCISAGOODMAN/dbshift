package com.dbshift.model;

import java.nio.file.Path;

public class MigrationFile {

    public enum ScriptType {
        MIGRATION, ROLLBACK
    }

    private final String version;
    private final String description;
    private final Path path;
    private final String checksum;
    private final ScriptType scriptType;
    private final String sql;

    public MigrationFile(String version, String description, Path path, String checksum,
                         ScriptType scriptType, String sql) {
        this.version = version;
        this.description = description;
        this.path = path;
        this.checksum = checksum;
        this.scriptType = scriptType;
        this.sql = sql;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public Path getPath() {
        return path;
    }

    public String getChecksum() {
        return checksum;
    }

    public ScriptType getScriptType() {
        return scriptType;
    }

    public String getSql() {
        return sql;
    }

    public int versionNumber() {
        return Integer.parseInt(version.replaceFirst("^V|^U", ""));
    }
}
