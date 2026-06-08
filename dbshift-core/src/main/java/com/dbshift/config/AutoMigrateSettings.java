package com.dbshift.config;

public class AutoMigrateSettings {

    private boolean enabled = false;
    private String environment = "dev";
    private boolean initOnStartup = true;
    private boolean failOnError = true;
    private String appliedBy = "app";
    private String targetVersion;
    private boolean requireConfirmForProd = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isInitOnStartup() {
        return initOnStartup;
    }

    public void setInitOnStartup(boolean initOnStartup) {
        this.initOnStartup = initOnStartup;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public void setAppliedBy(String appliedBy) {
        this.appliedBy = appliedBy;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public boolean isRequireConfirmForProd() {
        return requireConfirmForProd;
    }

    public void setRequireConfirmForProd(boolean requireConfirmForProd) {
        this.requireConfirmForProd = requireConfirmForProd;
    }
}
