package com.dbshift.model;

import java.util.ArrayList;
import java.util.List;

public class MigrationStatus {

    private String environment;
    private String latestAppliedVersion;
    private List<ChangelogEntry> applied = new ArrayList<>();
    private List<MigrationFile> pending = new ArrayList<>();
    private List<String> missingRollbackScripts = new ArrayList<>();

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getLatestAppliedVersion() {
        return latestAppliedVersion;
    }

    public void setLatestAppliedVersion(String latestAppliedVersion) {
        this.latestAppliedVersion = latestAppliedVersion;
    }

    public List<ChangelogEntry> getApplied() {
        return applied;
    }

    public void setApplied(List<ChangelogEntry> applied) {
        this.applied = applied;
    }

    public List<MigrationFile> getPending() {
        return pending;
    }

    public void setPending(List<MigrationFile> pending) {
        this.pending = pending;
    }

    public List<String> getMissingRollbackScripts() {
        return missingRollbackScripts;
    }

    public void setMissingRollbackScripts(List<String> missingRollbackScripts) {
        this.missingRollbackScripts = missingRollbackScripts;
    }
}
