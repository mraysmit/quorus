/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

/** Fail-closed assurance gate for OS-mounted SMB and NFS transports. */
public final class MountedFileSystemSecurity {
    private MountedFileSystemSecurity() { }

    public static void requireVerified(String protocol, boolean verified) throws ConnectionPolicyException {
        if (!verified) {
            throw new ConnectionPolicyException("Q-CONNECTION-" + protocol + "-MOUNT-UNVERIFIED",
                    protocol + " mount lacks an agent attestation for encrypted authenticated transport");
        }
    }

    public static boolean configured(String protocol) {
        return Boolean.getBoolean("quorus." + protocol.toLowerCase(java.util.Locale.ROOT)
                + ".encrypted-authenticated-mount");
    }
}
