package com.dbshift.dialect;

/**
 * Parameters for JDBC {@link java.sql.DatabaseMetaData} catalog/schema lookups.
 */
public record MetadataParams(String catalog, String schemaPattern) {
}
