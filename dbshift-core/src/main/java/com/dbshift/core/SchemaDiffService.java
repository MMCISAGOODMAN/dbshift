package com.dbshift.core;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dbshift.config.EnvironmentConfig;
import com.dbshift.model.EnvironmentDiff;

public class SchemaDiffService {

    private final SchemaMetadataLoader metadataLoader;

    public SchemaDiffService(SchemaMetadataLoader metadataLoader) {
        this.metadataLoader = metadataLoader;
    }

    public List<EnvironmentDiff.SchemaDifference> compare(EnvironmentConfig from, EnvironmentConfig to,
                                                            String fromName, String toName) throws SQLException {
        Map<String, Map<String, String>> fromTables = metadataLoader.loadTables(from);
        Map<String, Map<String, String>> toTables = metadataLoader.loadTables(to);
        List<EnvironmentDiff.SchemaDifference> differences = new ArrayList<>();

        Set<String> allTables = new HashSet<>();
        allTables.addAll(fromTables.keySet());
        allTables.addAll(toTables.keySet());

        for (String table : allTables.stream().sorted().toList()) {
            Map<String, String> left = fromTables.get(table);
            Map<String, String> right = toTables.get(table);
            if (left == null) {
                differences.add(diff("TABLE", table, "Exists in " + toName + " but missing in " + fromName, fromName, toName));
                continue;
            }
            if (right == null) {
                differences.add(diff("TABLE", table, "Exists in " + fromName + " but missing in " + toName, fromName, toName));
                continue;
            }
            compareColumns(table, left, right, fromName, toName, differences);
        }
        return differences;
    }

    private void compareColumns(String table, Map<String, String> leftCols, Map<String, String> rightCols,
                                String fromName, String toName, List<EnvironmentDiff.SchemaDifference> differences) {
        Set<String> all = new HashSet<>();
        all.addAll(leftCols.keySet());
        all.addAll(rightCols.keySet());
        for (String column : all.stream().sorted().toList()) {
            String left = leftCols.get(column);
            String right = rightCols.get(column);
            if (left == null) {
                differences.add(diff("COLUMN", table + "." + column,
                        "Column exists in " + toName + " only: " + right, fromName, toName));
            } else if (right == null) {
                differences.add(diff("COLUMN", table + "." + column,
                        "Column exists in " + fromName + " only: " + left, fromName, toName));
            } else if (!left.equalsIgnoreCase(right)) {
                differences.add(diff("COLUMN", table + "." + column,
                        fromName + ": " + left + " | " + toName + ": " + right, fromName, toName));
            }
        }
    }

    private EnvironmentDiff.SchemaDifference diff(String type, String name, String difference,
                                                  String source, String target) {
        EnvironmentDiff.SchemaDifference d = new EnvironmentDiff.SchemaDifference();
        d.setObjectType(type);
        d.setObjectName(name);
        d.setDifference(difference);
        d.setSourceEnv(source);
        d.setTargetEnv(target);
        return d;
    }
}
