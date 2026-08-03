package com.dbshift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PlaceholderResolverTest {

    @Test
    void replacesPlaceholders() {
        PlaceholderResolver resolver = new PlaceholderResolver(Map.of("schema", "app", "prefix", "t_"));
        String sql = "CREATE TABLE ${schema}.${prefix}users (id INT)";
        assertEquals("CREATE TABLE app.t_users (id INT)", resolver.apply(sql));
    }

    @Test
    void supportsDefaults() {
        PlaceholderResolver resolver = new PlaceholderResolver(Map.of());
        assertEquals("public", resolver.apply("${schema:public}"));
    }
}
