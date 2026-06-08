package com.dbshift.model;

import java.util.ArrayList;
import java.util.List;

public class RollbackPlan {

    private String environment;
    private String currentVersion;
    private String targetVersion;
    private int steps;
    private List<MigrationFile> rollbackScripts = new ArrayList<>();
    private List<SqlImpact> impacts = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private boolean dryRun;

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public List<MigrationFile> getRollbackScripts() {
        return rollbackScripts;
    }

    public void setRollbackScripts(List<MigrationFile> rollbackScripts) {
        this.rollbackScripts = rollbackScripts;
    }

    public List<SqlImpact> getImpacts() {
        return impacts;
    }

    public void setImpacts(List<SqlImpact> impacts) {
        this.impacts = impacts;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
