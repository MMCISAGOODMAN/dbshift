package com.dbshift.dialect;

public final class MariaDbDialect extends MySqlDialect {

    @Override
    public DialectType type() {
        return DialectType.MARIADB;
    }

    @Override
    public String displayName() {
        return "MariaDB";
    }
}
