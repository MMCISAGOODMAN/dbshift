package com.dbshift.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class DbshiftConfig {

    private Map<String, EnvironmentConfig> environments = new LinkedHashMap<>();
    private PathConfig migrations = new PathConfig("./db/migrations");
    private PathConfig rollback = new PathConfig("./db/rollback");
    private AutoMigrateSettings autoMigrate = new AutoMigrateSettings();

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
