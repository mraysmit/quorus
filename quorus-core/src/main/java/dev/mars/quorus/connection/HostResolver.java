/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@FunctionalInterface
public interface HostResolver {
    List<InetAddress> resolve(String host) throws Exception;

    static HostResolver system() {
        return host -> Arrays.asList(InetAddress.getAllByName(host));
    }
}
