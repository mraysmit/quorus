/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.protocol;

import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import dev.mars.quorus.transfer.TransferContext;
import dev.mars.quorus.transfer.TransferEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan items RT-03d and RT-03e: the public contracts of {@code quorus-core} carry no Vert.x type, so
 * callers depend only on the JDK (ADR-0012 decision 6). The blocking {@code transfer} is the contract,
 * not a deprecated fallback.
 */
@DisplayName("Protocol and engine contracts - no Vert.x")
class ProtocolContractIsVertxFreeTest {

    private static final List<Class<?>> CONTRACT = List.of(
            TransferProtocol.class, TransferEngine.class, ProtocolFactory.class, SimpleTransferEngine.class,
            HttpTransferProtocol.class, FtpTransferProtocol.class, SftpTransferProtocol.class,
            SmbTransferProtocol.class, NfsTransferProtocol.class,
            // RT-03e: the connection codec and the network topology service
            dev.mars.quorus.connection.ServiceConnectionJsonCodec.class,
            dev.mars.quorus.network.NetworkTopologyService.class, dev.mars.quorus.network.NetworkNode.class);

    @Test
    @DisplayName("No public or protected signature names an io.vertx type")
    void noPublicSignatureNamesAVertxType() {
        List<String> offending = new ArrayList<>();
        for (Class<?> type : CONTRACT) {
            List<Executable> members = new ArrayList<>(List.of(type.getDeclaredMethods()));
            members.addAll(List.of(type.getDeclaredConstructors()));
            for (Executable member : members) {
                if (!(Modifier.isPublic(member.getModifiers()) || Modifier.isProtected(member.getModifiers()))) {
                    continue;
                }
                List<Type> types = new ArrayList<>(List.of(member.getGenericParameterTypes()));
                types.addAll(List.of(member.getGenericExceptionTypes()));
                if (member instanceof Method method) {
                    types.add(method.getGenericReturnType());
                }
                for (Type signatureType : types) {
                    if (signatureType.getTypeName().contains("io.vertx")) {
                        offending.add(type.getSimpleName() + "." + name(member) + " : " + signatureType.getTypeName());
                    }
                }
            }
        }
        assertTrue(offending.isEmpty(), () -> "Vert.x in the contract: " + offending);
    }

    @Test
    @DisplayName("The blocking transfer is the contract, not a deprecated fallback")
    void blockingTransferIsNotDeprecated() throws Exception {
        Method transfer = TransferProtocol.class.getMethod("transfer", TransferRequest.class, TransferContext.class);
        assertFalse(transfer.isAnnotationPresent(Deprecated.class));
    }

    private static String name(Executable member) {
        return member instanceof Constructor<?> ? "<init>" : member.getName();
    }
}
