/*
 * Copyright 2026 Mark Andrew Ray-Smith Cityline Ltd
 * Licensed under the Apache License, Version 2.0.
 */
package dev.mars.quorus.connection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes an agent-side staged connection test without returning endpoints or secret material. */
public final class ConnectionValidationService {
    private final GovernedConnectionResolver resolver;
    private final ConnectionProbe probe;

    public ConnectionValidationService(GovernedConnectionResolver resolver, ConnectionProbe probe) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    public Result validateAtAgent(ServiceConnection connection, SecretReference reference,
                                  ConnectionAccessRequest request) {
        List<Stage> stages = new ArrayList<>();
        try (ResolvedConnection resolved = resolver.resolveAtAgent(connection, reference, request)) {
            stages.add(new Stage("POLICY", "PASS", "Controller and agent policy approved"));
            stages.add(new Stage("DNS", "PASS", "Resolved address set matched the approved pin"));
            List<Stage> probed = probe.probe(resolved);
            if (probed != null) stages.addAll(probed);
            boolean valid = stages.stream().noneMatch(stage -> "FAIL".equals(stage.status()));
            return new Result(connection.serviceConnectionId(), valid ? "VALID" : "INVALID",
                    List.copyOf(stages), connection.policyVersion(), Instant.now());
        } catch (ConnectionPolicyException e) {
            stages.add(new Stage("POLICY", "FAIL", e.decisionCode()));
            return new Result(connection.serviceConnectionId(), "INVALID", List.copyOf(stages),
                    connection.policyVersion(), Instant.now());
        } catch (Exception e) {
            stages.add(new Stage("CONNECTION", "FAIL", "Q-CONNECTION-PROBE-FAILED"));
            return new Result(connection.serviceConnectionId(), "INVALID", List.copyOf(stages),
                    connection.policyVersion(), Instant.now());
        }
    }

    @FunctionalInterface
    public interface ConnectionProbe {
        List<Stage> probe(ResolvedConnection connection) throws Exception;
    }
    public record Stage(String stage, String status, String detail) { }
    public record Result(String serviceConnectionId, String status, List<Stage> stages,
                         int policyVersion, Instant validatedAt) {
        public Result { stages = List.copyOf(stages); }
    }
}
