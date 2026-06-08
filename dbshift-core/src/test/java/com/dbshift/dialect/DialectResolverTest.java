package com.dbshift.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.dbshift.config.EnvironmentConfig;

class DialectResolverTest {

    @Test
    void resolvesFromJdbcUrl() {
        assertInstanceOf(MySqlDialect.class, DialectResolver.fromUrl("jdbc:mysql://localhost/app"));
        assertInstanceOf(MariaDbDialect.class, DialectResolver.fromUrl("jdbc:mariadb://localhost/app"));
        assertInstanceOf(PostgreSqlDialect.class, DialectResolver.fromUrl("jdbc:postgresql://localhost/app"));
        assertInstanceOf(H2Dialect.class, DialectResolver.fromUrl("jdbc:h2:mem:test"));
        assertInstanceOf(SqliteDialect.class, DialectResolver.fromUrl("jdbc:sqlite:./app.db"));
        assertInstanceOf(SqlServerDialect.class, DialectResolver.fromUrl("jdbc:sqlserver://localhost;databaseName=app"));
        assertInstanceOf(OracleDialect.class, DialectResolver.fromUrl("jdbc:oracle:thin:@localhost:1521/app"));
        assertInstanceOf(GenericDialect.class, DialectResolver.fromUrl("jdbc:unknown://localhost/app"));
    }

    @Test
    void explicitDialectOverridesUrl() {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setUrl("jdbc:mysql://localhost/app");
        config.setDialect("postgresql");

        assertInstanceOf(PostgreSqlDialect.class, DialectResolver.resolve(config));
    }

    @Test
    void listsSupportedDialects() {
        assertEquals(8, DialectResolver.supportedDialects().size());
    }
}
