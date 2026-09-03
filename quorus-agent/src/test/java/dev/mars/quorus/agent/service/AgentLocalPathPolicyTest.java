/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.agent.service;

import dev.mars.quorus.core.TransferDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentLocalPathPolicyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void confinesUploadsAndDownloadsToTheirConfiguredRoots() throws Exception {
        Path uploadRoot = Files.createDirectory(temporaryDirectory.resolve("uploads"));
        Path downloadRoot = Files.createDirectory(temporaryDirectory.resolve("downloads"));
        Path upload = Files.writeString(uploadRoot.resolve("statement.csv"), "test");
        AgentLocalPathPolicy policy = new AgentLocalPathPolicy(uploadRoot, downloadRoot);

        assertDoesNotThrow(() -> policy.authorize(upload.toUri(), TransferDirection.UPLOAD));
        assertDoesNotThrow(() -> policy.authorize(downloadRoot.resolve("received.csv").toUri(),
                TransferDirection.DOWNLOAD));
        assertThrows(SecurityException.class, () -> policy.authorize(
                temporaryDirectory.resolve("outside-upload.csv").toUri(), TransferDirection.UPLOAD));
        assertThrows(SecurityException.class, () -> policy.authorize(
                temporaryDirectory.resolve("outside-download.csv").toUri(), TransferDirection.DOWNLOAD));
    }

    @Test
    void rejectsSymlinkEscapesFromAnApprovedRoot() throws Exception {
        Path uploadRoot = Files.createDirectory(temporaryDirectory.resolve("uploads"));
        Path downloadRoot = Files.createDirectory(temporaryDirectory.resolve("downloads"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Path outsideFile = Files.writeString(outside.resolve("secret.txt"), "secret");
        Path uploadLink = uploadRoot.resolve("escape.txt");
        Path downloadLink = downloadRoot.resolve("escape");
        try {
            Files.createSymbolicLink(uploadLink, outsideFile);
            Files.createSymbolicLink(downloadLink, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable: " + e.getMessage());
        }
        AgentLocalPathPolicy policy = new AgentLocalPathPolicy(uploadRoot, downloadRoot);

        assertThrows(SecurityException.class,
                () -> policy.authorize(uploadLink.toUri(), TransferDirection.UPLOAD));
        assertThrows(SecurityException.class,
                () -> policy.authorize(downloadLink.resolve("new.txt").toUri(), TransferDirection.DOWNLOAD));
    }
}
