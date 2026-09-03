/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;

/** Strict SHA-256 SSH host-key pin verification shared by validation and SFTP runtime code. */
public final class SftpHostKeyPolicy {
    private SftpHostKeyPolicy() { }

    public static void requireApproved(byte[] encodedHostKey, Set<String> approvedFingerprints)
            throws ConnectionPolicyException {
        String observed = sha256Fingerprint(encodedHostKey);
        if (approvedFingerprints == null || approvedFingerprints.stream().noneMatch(observed::equals)) {
            throw new ConnectionPolicyException("Q-CONNECTION-SSH-HOST-KEY-DENIED",
                    "SSH host key is not approved");
        }
    }

    public static String sha256Fingerprint(byte[] encodedHostKey) {
        try {
            return "SHA256:" + Base64.getEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(encodedHostKey));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
