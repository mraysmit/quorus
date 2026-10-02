/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Register item ENG-09: a protocol adapter is one shared instance serving every transfer of its
 * protocol, so it must not hold per-transfer connection state, and cancellation is interruption of
 * the transfer's own thread (SimpleTransferEngine), not an adapter-wide abort.
 */
@DisplayName("Protocol adapters - cancellation contract")
class TransferProtocolCancellationContractTest {

    private static final List<Class<? extends TransferProtocol>> ADAPTERS = List.of(
            HttpTransferProtocol.class, FtpTransferProtocol.class, SftpTransferProtocol.class,
            SmbTransferProtocol.class, NfsTransferProtocol.class);

    /** Types that represent one transfer's connection. */
    private static final Pattern CONNECTION_TYPE = Pattern.compile("FtpClient|SftpClient|CloseableHttpClient");

    @Test
    @DisplayName("TransferProtocol has no adapter-wide abort; cancellation is interruption")
    void transferProtocolHasNoAdapterWideAbort() {
        assertThrows(NoSuchMethodException.class, () -> TransferProtocol.class.getMethod("abort"));
    }

    @Test
    @DisplayName("Shared adapters hold no per-transfer connection in an instance field")
    void adaptersHoldNoPerTransferConnectionState() {
        List<String> offending = new ArrayList<>();
        for (Class<?> adapter : ADAPTERS) {
            for (Field field : adapter.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && CONNECTION_TYPE.matcher(field.getGenericType().getTypeName()).find()) {
                    offending.add(adapter.getSimpleName() + "." + field.getName() + " : " + field.getGenericType());
                }
            }
        }
        assertTrue(offending.isEmpty(), () -> "per-transfer connection state in a shared adapter: " + offending);
    }
}
