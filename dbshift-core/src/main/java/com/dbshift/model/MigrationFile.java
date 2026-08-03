package com.dbshift.model;

import java.nio.file.Path;

public class MigrationFile {

    public enum ScriptType {
        MIGRATION, ROLLBACK, REPEATABLE, BASELINE
    }

    private final String version;
    private final String description;
    private final Path path;
    private final String checksum;
    private final ScriptType scriptType;
    private final String sql;
    private final Version parsedVersion;

    public MigrationFile(String version, String description, Path path, String checksum,
                         ScriptType scriptType, String sql) {
        this.version = version;
        this.description = description;
        this.path = path;
        this.checksum = checksum;
        this.scriptType = scriptType;
        this.sql = sql;
        this.parsedVersion = (scriptType == ScriptType.MIGRATION || scriptType == ScriptType.ROLLBACK)
                ? Version.parse(version)
                : null;
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

    public Version getParsedVersion() {
        return parsedVersion;
    }

    /** @deprecated use {@link #getParsedVersion()} */
    @Deprecated
    public int versionNumber() {
        if (parsedVersion == null || parsedVersion.parts().isEmpty()) {
            return 0;
        }
        return parsedVersion.parts().get(0);
    }

    public boolean isRepeatable() {
        return scriptType == ScriptType.REPEATABLE;
    }

    public boolean isVersioned() {
        return scriptType == ScriptType.MIGRATION;
    }
}
