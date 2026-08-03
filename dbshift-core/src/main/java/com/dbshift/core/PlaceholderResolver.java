package com.dbshift.core;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code ${name}} placeholders in SQL. Supports {@code ${name:default}}.
 */
public final class PlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

    private final Map<String, String> placeholders;

    public PlaceholderResolver(Map<String, String> placeholders) {
        this.placeholders = placeholders == null ? Map.of() : Collections.unmodifiableMap(placeholders);
    }

    public String apply(String sql) {
        if (sql == null || sql.isEmpty() || placeholders.isEmpty() && !sql.contains("${")) {
            return sql;
        }
        Matcher matcher = PLACEHOLDER.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            String value = placeholders.get(key);
            if (value == null) {
                value = defaultValue != null ? defaultValue : matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }
}
