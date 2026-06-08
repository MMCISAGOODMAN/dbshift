package com.dbshift.core;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.dbshift.config.EnvironmentConfig;
import com.dbshift.dialect.DatabaseDialect;
import com.dbshift.dialect.DialectResolver;
import com.dbshift.dialect.MetadataParams;

/**
 * Loads table/column metadata via JDBC {@link DatabaseMetaData} (database-agnostic).
 */
public class SchemaMetadataLoader {

    private static final Set<String> LOOSE_TYPES = Set.of(
            "TEXT", "BLOB", "CLOB", "NCLOB", "JSON", "JSONB", "BYTEA", "UUID"
    );

    private final ConnectionFactory connectionFactory;

    public SchemaMetadataLoader(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Map<String, Map<String, String>> loadTables(EnvironmentConfig config) throws SQLException {
        DatabaseDialect dialect = DialectResolver.resolve(config);
        try (Connection conn = connectionFactory.open(config)) {
            return loadTables(conn, dialect, config);
        }
    }

    public Map<String, Map<String, String>> loadTables(Connection conn, DatabaseDialect dialect,
                                                       EnvironmentConfig config) throws SQLException {
        Map<String, Map<String, String>> tables = new TreeMap<>();
        MetadataParams params = dialect.metadataParams(conn, config);
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet rs = meta.getColumns(params.catalog(), params.schemaPattern(), "%", "%")) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (ChangelogRepository.TABLE_NAME.equalsIgnoreCase(table)) {
                    continue;
                }
                String column = rs.getString("COLUMN_NAME");
                String type = formatColumnType(rs);
                tables.computeIfAbsent(table, k -> new HashMap<>()).put(column, type);
            }
        }
        return tables;
    }

    private String formatColumnType(ResultSet rs) throws SQLException {
        String typeName = rs.getString("TYPE_NAME");
        if (typeName == null) {
            return "UNKNOWN";
        }
        String upper = typeName.toUpperCase(Locale.ROOT);
        int size = rs.getInt("COLUMN_SIZE");
        int decimal = rs.getInt("DECIMAL_DIGITS");

        if (decimal > 0 && size > 0) {
            return upper + "(" + size + "," + decimal + ")";
        }
        if (size > 0 && !LOOSE_TYPES.contains(upper)) {
            return upper + "(" + size + ")";
        }
        return upper;
    }
}
