package com.dbshift.core;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.dbshift.config.EnvironmentConfig;

public class ConnectionFactory {

    private final DataSource dataSource;

    public ConnectionFactory() {
        this(null);
    }

    public ConnectionFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Connection open(EnvironmentConfig config) throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        if (config == null || config.getUrl() == null || config.getUrl().isBlank()) {
            throw new SQLException("No DataSource configured and environment url is empty");
        }
        String user = config.getUser() != null ? config.getUser() : "";
        String password = config.getPassword() != null ? config.getPassword() : "";
        return java.sql.DriverManager.getConnection(config.getUrl(), user, password);
    }

    public boolean usesDataSource() {
        return dataSource != null;
    }
}
