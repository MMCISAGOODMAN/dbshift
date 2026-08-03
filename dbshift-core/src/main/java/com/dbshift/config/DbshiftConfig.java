package com.dbshift.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class DbshiftConfig {

    private Map<String, EnvironmentConfig> environments = new LinkedHashMap<>();
    private PathConfig migrations = new PathConfig("./db/migrations");
    private PathConfig rollback = new PathConfig("./db/rollback");
    private AutoMigrateSettings autoMigrate = new AutoMigrateSettings();
    private Map<String, String> placeholders = new LinkedHashMap<>();
    private boolean baselineOnMigrate = false;
    private String baselineVersion = "1";
    private boolean outOfOrder = false;

    public Map<String, EnvironmentConfig> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Map<String, EnvironmentConfig> environments) {
        this.environments = environments;
    }

    public PathConfig getMigrations() {
        return migrations;
    }

    public void setMigrations(PathConfig migrations) {
        this.migrations = migrations;
    }

    public PathConfig getRollback() {
        return rollback;
    }

    public void setRollback(PathConfig rollback) {
        this.rollback = rollback;
    }

    public AutoMigrateSettings getAutoMigrate() {
        return autoMigrate;
    }

    public void setAutoMigrate(AutoMigrateSettings autoMigrate) {
        this.autoMigrate = autoMigrate;
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    public void setPlaceholders(Map<String, String> placeholders) {
        this.placeholders = placeholders != null ? placeholders : new LinkedHashMap<>();
    }

    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        this.baselineOnMigrate = baselineOnMigrate;
    }

    public String getBaselineVersion() {
        return baselineVersion;
    }

    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    public boolean isOutOfOrder() {
        return outOfOrder;
    }

    public void setOutOfOrder(boolean outOfOrder) {
        this.outOfOrder = outOfOrder;
    }

    public EnvironmentConfig requireEnvironment(String name) {
        EnvironmentConfig env = environments.get(name);
        if (env == null) {
            throw new IllegalArgumentException("Unknown environment: " + name
                    + ". Available: " + String.join(", ", environments.keySet()));
        }
        return env;
    }

    public static class PathConfig {
        private String dir;

        public PathConfig() {
        }

        public PathConfig(String dir) {
            this.dir = dir;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }
}
