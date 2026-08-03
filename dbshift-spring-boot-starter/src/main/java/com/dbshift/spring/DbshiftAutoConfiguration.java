package com.dbshift.spring;

import java.nio.file.Path;

import javax.sql.DataSource;

import com.dbshift.DbshiftBootstrap;
import com.dbshift.DbshiftEngine;
import com.dbshift.config.AutoMigrateSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 集成：应用启动时自动执行数据库迁移。
 *
 * <pre>
 * dbshift:
 *   auto-migrate: true
 *   config-file: ./dbshift.yaml
 *   environment: dev
 *   use-data-source: true
 * </pre>
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(DbshiftSpringProperties.class)
@ConditionalOnProperty(prefix = "dbshift", name = "auto-migrate", havingValue = "true")
public class DbshiftAutoConfiguration {

    @Bean
    ApplicationRunner dbshiftMigrationRunner(DbshiftSpringProperties properties,
                                             ObjectProvider<DataSource> dataSourceProvider) {
        return args -> runMigration(properties, dataSourceProvider.getIfAvailable());
    }

    static void runMigration(DbshiftSpringProperties properties, DataSource dataSource) throws Exception {
        Path configPath = Path.of(properties.getConfigFile());
        DataSource ds = properties.isUseDataSource() ? dataSource : null;
        DbshiftEngine engine = DbshiftEngine.fromYaml(configPath, ds);

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
