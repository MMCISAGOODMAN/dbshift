package com.dbshift;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ExampleMigrationValidationTest {

    @Test
    void exampleMigrationsAreValid() throws Exception {
        Path config = Path.of("src/test/resources/dbshift-test.yaml");
        DbshiftEngine engine = DbshiftEngine.fromYaml(config);
        var validation = engine.validate();
        assertTrue(validation.isValid(), String.join("; ", validation.getErrors()));
    }
}
