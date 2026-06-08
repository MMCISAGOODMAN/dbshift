package com.dbshift.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dbshift")
public class DbshiftSpringProperties {

    /** Enable auto-migration on Spring Boot startup. */
    private boolean autoMigrate = false;

    /** Path to dbshift.yaml, default ./dbshift.yaml */
    private String configFile = "./dbshift.yaml";

    /** Override environment from yaml autoMigrate.environment */
    private String environment;

    /** Fail application startup if migration fails. */
    private boolean failOnError = true;

    public boolean isAutoMigrate() {
        return autoMigrate;
    }

    public void setAutoMigrate(boolean autoMigrate) {
        this.autoMigrate = autoMigrate;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }
}
