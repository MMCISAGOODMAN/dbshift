package com.dbshift.core;

import java.sql.Connection;
import java.sql.SQLException;

import com.dbshift.config.EnvironmentConfig;

public class ConnectionFactory {

    public Connection open(EnvironmentConfig config) throws SQLException {
        return java.sql.DriverManager.getConnection(
                config.getUrl(),
                config.getUser(),
                config.getPassword()
        );
    }
}
