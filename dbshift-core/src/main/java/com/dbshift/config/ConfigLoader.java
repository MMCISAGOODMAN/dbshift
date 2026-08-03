package com.dbshift.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

public class ConfigLoader {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

    public DbshiftConfig load(Path configFile) throws IOException {
        if (!Files.exists(configFile)) {
            throw new IOException("Config file not found: " + configFile.toAbsolutePath());
        }
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configFile)) {
            DbshiftConfig config = yaml.loadAs(in, DbshiftConfig.class);
            resolveEnvVars(config);
            return config;
        }
    }

    private void resolveEnvVars(DbshiftConfig config) {
        for (EnvironmentConfig env : config.getEnvironments().values()) {
            env.setUrl(resolve(env.getUrl()));
            env.setUser(resolve(env.getUser()));
            env.setPassword(resolve(env.getPassword()));
        }
        if (config.getMigrations() != null) {
            config.getMigrations().setDir(resolve(config.getMigrations().getDir()));
        }
        if (config.getRollback() != null) {
            config.getRollback().setDir(resolve(config.getRollback().getDir()));
        }
        if (config.getAutoMigrate() != null) {
            AutoMigrateSettings auto = config.getAutoMigrate();
            auto.setEnvironment(resolve(auto.getEnvironment()));
            auto.setAppliedBy(resolve(auto.getAppliedBy()));
            if (auto.getTargetVersion() != null) {
                auto.setTargetVersion(resolve(auto.getTargetVersion()));
            }
        }
        if (config.getBaselineVersion() != null) {
            config.setBaselineVersion(resolve(config.getBaselineVersion()));
        }
        if (config.getPlaceholders() != null && !config.getPlaceholders().isEmpty()) {
            Map<String, String> resolved = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> entry : config.getPlaceholders().entrySet()) {
                resolved.put(entry.getKey(), resolve(entry.getValue()));
            }
            config.setPlaceholders(resolved);
        }
    }

    String resolve(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = ENV_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String defaultValue = matcher.group(2);
            String envValue = System.getenv(envName);
            if (envValue == null) {
                envValue = System.getProperty(envName);
            }
            if (envValue == null) {
                envValue = defaultValue != null ? defaultValue : "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
