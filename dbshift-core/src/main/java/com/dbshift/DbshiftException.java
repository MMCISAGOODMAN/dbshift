package com.dbshift;

public class DbshiftException extends RuntimeException {

    public DbshiftException(String message) {
        super(message);
    }

    public DbshiftException(String message, Throwable cause) {
        super(message, cause);
    }
}
