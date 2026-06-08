package com.dbshift.spring;

import java.nio.file.Path;

import com.dbshift.DbshiftBootstrap;
import com.dbshift.DbshiftEngine;
import com.dbshift.config.AutoMigrateSettings;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 集成：应用启动时自动执行数据库迁移。
 *
 * application.yml:
 * <pre>
 * dbshift:
 *   auto-migrate: true
 *   config-file: ./dbshift.yaml
 *   environment: dev
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(DbshiftSpringProperties.class)
@ConditionalOnProperty(prefix = "dbshift", name = "auto-migrate", havingValue = "true")
public class DbshiftAutoConfiguration {

    @Bean
    ApplicationRunner dbshiftMigrationRunner(DbshiftSpringProperties properties) {
        return args -> runMigration(properties);
    }

    static void runMigration(DbshiftSpringProperties properties) throws Exception {
        Path configPath = Path.of(properties.getConfigFile());
        DbshiftEngine engine = DbshiftEngine.fromYaml(configPath);

        AutoMigrateSettings yamlAuto = engine.config().getAutoMigrate();
        if (yamlAuto != null) {
            yamlAuto.setEnabled(true);
            yamlAuto.setFailOnError(properties.isFailOnError());
            if (properties.getEnvironment() != null && !properties.getEnvironment().isBlank()) {
                yamlAuto.setEnvironment(properties.getEnvironment());
            }
        }

        if (properties.getEnvironment() != null && !properties.getEnvironment().isBlank()) {
            DbshiftBootstrap.start(engine, properties.getEnvironment());
        } else {
            DbshiftBootstrap.start(engine);
        }
    }
}
