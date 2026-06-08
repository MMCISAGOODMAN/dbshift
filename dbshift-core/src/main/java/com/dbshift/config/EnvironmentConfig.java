package com.dbshift.config;

public class EnvironmentConfig {

    private String url;
    private String user;
    private String password;
    /** Optional explicit dialect: mysql, mariadb, postgresql, h2, sqlite, sqlserver, oracle, generic */
    private String dialect;
    /** Optional schema/catalog scope for metadata and schema diff */
    private String schema;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
