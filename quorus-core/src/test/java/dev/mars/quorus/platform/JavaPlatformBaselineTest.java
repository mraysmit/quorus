/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.platform;

import dev.mars.quorus.protocol.TransferProtocol;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platform baseline contract (plan workstream RT-01, ADR-0012).
 *
 * <p>Quorus follows each six-monthly Java feature release. When the baseline moves, this test
 * changes first and must fail until the build targets the new release.
 */
class JavaPlatformBaselineTest {

    private static final int JAVA_BASELINE = 27;
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final int FIRST_CLASS_FILE_MAJOR_OFFSET = 44;

    @Test
    void productionClassesTargetTheJavaBaseline() throws IOException {
        int major = classFileMajorVersion(TransferProtocol.class);

        assertEquals(FIRST_CLASS_FILE_MAJOR_OFFSET + JAVA_BASELINE, major,
                "quorus-core production classes must be compiled for Java " + JAVA_BASELINE
                        + " (class-file major version " + (FIRST_CLASS_FILE_MAJOR_OFFSET + JAVA_BASELINE)
                        + "), but found major version " + major
                        + " (Java " + (major - FIRST_CLASS_FILE_MAJOR_OFFSET) + ")");
    }

    @Test
    void testsRunOnTheJavaBaselineOrLater() {
        int runtimeFeature = Runtime.version().feature();

        assertTrue(runtimeFeature >= JAVA_BASELINE,
                "tests must run on Java " + JAVA_BASELINE + " or later, but the runtime is Java " + runtimeFeature);
    }

    private static int classFileMajorVersion(Class<?> type) throws IOException {
        String resource = type.getSimpleName() + ".class";
        try (InputStream raw = type.getResourceAsStream(resource)) {
            assertNotNull(raw, "class file not found for " + type.getName());
            DataInputStream in = new DataInputStream(raw);
            assertEquals(CLASS_FILE_MAGIC, in.readInt(), "not a class file: " + type.getName());
            in.readUnsignedShort(); // minor version
            return in.readUnsignedShort();
        }
    }
}
