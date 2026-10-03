/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Register decision DR-Q5: the root pom version is the one product version. */
class ProductVersionTest {

    @Test
    void theProductVersionIsTheRootPomVersion() throws Exception {
        assertEquals(PomVersion.of(Path.of("..", "pom.xml")), ProductVersion.get());
    }

    @Test
    void aBuildThatDidNotFilterTheVersionIsRejected() {
        for (String unfiltered : new String[]{"${project.version}", "", "  "}) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> ProductVersion.require(unfiltered), "'" + unfiltered + "'");
            assertTrue(error.getMessage().contains("quorus-build.properties"), error.getMessage());
        }
        assertThrows(IllegalStateException.class, () -> ProductVersion.require(null));
        assertEquals("1.2.3", ProductVersion.require(" 1.2.3 "));
    }
}
