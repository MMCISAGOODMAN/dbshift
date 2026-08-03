package com.dbshift.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void parsesFlywayStyleVersions() {
        assertEquals(0, Version.parse("V1").compareTo(Version.parse("V001")));
        assertTrue(Version.parse("V1.2").compareTo(Version.parse("V1.10")) < 0);
        assertTrue(Version.parse("V1_2_3").compareTo(Version.parse("V1.2.2")) > 0);
        assertEquals("V1.2.3", Version.parse("V1_2_3").display());
    }
}
