/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.controller.raft;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static dev.mars.quorus.testing.TestFutureUtils.awaitSuccess;
import static dev.mars.quorus.testing.TestFutureUtils.eventually;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1-1 container-recreation durability acceptance.
 *
 * <p>{@link ThreeControllerDurableRestartTest} restarts in-process controllers against the
 * same temporary directory. That proves recovery across a process lifetime but not across a
 * container lifetime: it never destroys the container filesystem, never exercises the
 * shipped image's storage configuration, and never proves that the deployed volume mount is
 * the thing actually holding authoritative state.</p>
 *
 * <p>This test destroys the controller containers entirely — {@code docker compose down}
 * without {@code --volumes} — and recreates them from the same image against the surviving
 * named volumes. It is the deployment-shaped gate required by register item {@code R1-1}.</p>
 *
 * <h3>Evidence boundary</h3>
 * <p>Passing this test proves durability across container destruction and recreation on the
 * Docker engine it runs against. It does <em>not</em> prove {@code R1-3} machine power-loss
 * durability: a graceful container stop flushes differently from an unclean host power cut,
 * and on Docker Desktop the containers run inside a virtual machine with its own page cache.
 * That gate remains open and separately evidenced.</p>
 *
 * <p>Requires Docker. Excluded from the default cycle; run with
 * {@code mvn test -Dgroups=docker}.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-09-07
 */
@Tag("docker")
@DisplayName("R1-1 container-recreation durability")
class ContainerRecreationDurabilityTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final String TENANT_ID = "regulated-bank-a";
    private static final int NODE_COUNT = 3;

    private static DockerComposeCluster cluster;
    private static Vertx vertx;
    private static WebClient webClient;

    @BeforeAll
    static void startCluster() {
        SharedDockerCluster.buildImageIfAbsent();
        vertx = Vertx.vertx();
        webClient = WebClient.create(vertx);
        cluster = new DockerComposeCluster(
                "src/test/resources/docker-compose-3node-durable.yml",
                "quorus-r1-recreate-" + UUID.randomUUID().toString().substring(0, 8));
        cluster.up();
    }

    @AfterAll
    static void stopCluster() {
        if (cluster != null) {
            cluster.downRemovingVolumes();
        }
        if (webClient != null) {
            webClient.close();
        }
        if (vertx != null) {
            awaitSuccess(vertx.close(), Duration.ofSeconds(10));
        }
    }

    @Test
    @DisplayName("A committed transfer survives destroying and recreating every controller container")
    void committedTransferSurvivesContainerRecreation() {
        String jobId = "r1-recreate-" + UUID.randomUUID().toString().substring(0, 8);

        awaitClusterReady();
        submitTransfer(jobId);
        awaitVisibleOnEveryNode(jobId);

        // Destroy the containers, keeping the named volumes: this is the recreation case.
        cluster.downKeepingVolumes();
        cluster.up();

        awaitClusterReady();
        awaitVisibleOnEveryNode(jobId);

        // Assert recovered content, not merely key presence: a recreated controller that
        // returned an empty shell for a known jobId would still satisfy a presence check.
        //
        // Tenant ownership is deliberately not asserted here. This plaintext fixture runs with
        // security disabled, so there is no authenticated identity for the controller to check
        // the job's tenant against, and GET /api/v1/transfers/{jobId} does not echo tenantId.
        // Tenant survival across restart is asserted directly against authoritative state by
        // ThreeControllerDurableRestartTest; this test owns the container boundary.
        for (String endpoint : cluster.controllerEndpoints(NODE_COUNT)) {
            JsonObject recovered = getTransfer(endpoint, jobId);
            assertEquals(jobId, recovered.getString("jobId"),
                    "The recreated cluster must return the same authoritative transfer");
            assertEquals("https://payments.example.test/" + jobId + ".dat",
                    recovered.getString("sourceUri"),
                    "Recovered transfer content must match what was committed, not an empty shell");
            assertEquals(8_192L, recovered.getLong("totalBytes"),
                    "Recovered transfer size must survive container recreation");
            assertEquals("PENDING", recovered.getString("status"),
                    "Recovered lifecycle state must survive container recreation");
        }
    }

    @Test
    @DisplayName("Recovery after container recreation uses the durable snapshot once the WAL is compacted")
    void recoveryUsesDurableSnapshotAfterCompaction() {
        awaitClusterReady();

        // Exceed the fixture's snapshot threshold so a real snapshot and a real WAL prefix
        // compaction happen before the containers are destroyed.
        String firstJobId = "r1-compact-" + UUID.randomUUID().toString().substring(0, 8);
        submitTransfer(firstJobId);
        for (int i = 0; i < 6; i++) {
            submitTransfer("r1-compact-filler-" + i + "-" + UUID.randomUUID().toString().substring(0, 6));
        }
        awaitVisibleOnEveryNode(firstJobId);

        // The leader is the node that takes snapshots and compacts its WAL; a follower that is
        // already up to date never receives an InstallSnapshot and so writes no snapshot file.
        // Asserting against a fixed node would therefore assert the wrong thing.
        String leaderVolume = cluster.volumeName(leaderNodeName() + "-data");
        awaitSuccess(eventually(vertx,
                () -> containsSnapshotArtifact(DockerComposeCluster.listVolumeContents(leaderVolume, "/raft")),
                TIMEOUT), TIMEOUT.plusSeconds(5));

        List<String> beforeRecreation = DockerComposeCluster.listVolumeContents(leaderVolume, "/raft");
        assertTrue(containsSnapshotArtifact(beforeRecreation),
                "A durable snapshot artifact must exist on the volume before recreation, "
                + "otherwise this test proves WAL replay rather than snapshot recovery. Found: "
                + beforeRecreation);

        cluster.downKeepingVolumes();

        // The volume must still hold the durable state with no controller process alive.
        List<String> whileDestroyed = DockerComposeCluster.listVolumeContents(leaderVolume, "/raft");
        assertFalse(whileDestroyed.isEmpty(),
                "Durable Raft state must remain on the volume while no container exists");
        assertTrue(containsSnapshotArtifact(whileDestroyed),
                "The snapshot must outlive the container that wrote it. Found: " + whileDestroyed);

        cluster.up();
        awaitClusterReady();
        awaitVisibleOnEveryNode(firstJobId);
    }

    @Test
    @DisplayName("Recreating one container while the other two hold quorum loses no committed state")
    void singleNodeRecreationRejoinsWithoutDataLoss() {
        awaitClusterReady();

        String jobId = "r1-rolling-" + UUID.randomUUID().toString().substring(0, 8);
        submitTransfer(jobId);
        awaitVisibleOnEveryNode(jobId);

        // Recreate a follower so the surviving two nodes keep quorum throughout. This is the
        // rolling-replacement shape of a real deployment, not a full-cluster outage.
        String follower = followerNodeName();
        cluster.recreateService(follower);

        awaitClusterReady();
        awaitVisibleOnEveryNode(jobId);

        JsonObject recovered = getTransfer(endpointFor(follower), jobId);
        assertEquals(jobId, recovered.getString("jobId"),
                "A recreated follower must serve the committed transfer after rejoining");
    }

    @Test
    @DisplayName("Negative control: removing the volumes does lose state, so the assertions have teeth")
    void removingVolumesLosesStateProvingTheGateIsNotVacuous() {
        // A durability test that can never fail proves nothing. This control runs the same
        // sequence in an isolated compose project but destroys the volumes as well, and
        // requires that the committed transfer is then gone. If this ever passes with the
        // state still present, the fixture is not actually exercising the durable volume and
        // every other assertion in this class is suspect.
        DockerComposeCluster control = new DockerComposeCluster(
                "src/test/resources/docker-compose-3node-durable.yml",
                "quorus-r1-control-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            control.up();
            String jobId = "r1-control-" + UUID.randomUUID().toString().substring(0, 8);

            awaitReady(control);
            submitTransferTo(control, jobId);
            awaitVisibleOnEveryNode(control, jobId);

            control.downRemovingVolumes();
            control.up();
            awaitReady(control);

            for (String endpoint : control.controllerEndpoints(NODE_COUNT)) {
                assertNull(findTransfer(endpoint, jobId),
                        "With volumes removed the transfer must be gone; if it survives, the "
                        + "fixture is not proving volume-backed durability");
            }
        } finally {
            control.downRemovingVolumes();
        }
    }

    private String endpointFor(String nodeName) {
        int index = Integer.parseInt(nodeName.substring("controller".length()));
        return cluster.controllerEndpoints(NODE_COUNT).get(index - 1);
    }

    /** Returns the compose service name of a current follower. */
    private String followerNodeName() {
        String leader = leaderNodeName();
        for (int i = 1; i <= NODE_COUNT; i++) {
            String candidate = "controller" + i;
            if (!candidate.equals(leader)) {
                return candidate;
            }
        }
        throw new AssertionError("No follower found alongside leader " + leader);
    }

    /** Returns the compose service name of the current leader, e.g. {@code controller2}. */
    private String leaderNodeName() {
        List<String> endpoints = cluster.controllerEndpoints(NODE_COUNT);
        for (String endpoint : endpoints) {
            try {
                URI uri = URI.create(endpoint);
                HttpResponse<io.vertx.core.buffer.Buffer> response = awaitSuccess(
                        webClient.get(uri.getPort(), uri.getHost(), "/raft/status").send(), HTTP_TIMEOUT);
                if (response.statusCode() == 200) {
                    JsonObject status = response.bodyAsJsonObject();
                    if (Boolean.TRUE.equals(status.getBoolean("isLeader"))) {
                        return status.getString("nodeId");
                    }
                }
            } catch (RuntimeException e) {
                // try the next node
            }
        }
        throw new AssertionError("No leader reported by any controller");
    }

    private static boolean containsSnapshotArtifact(List<String> entries) {
        return entries.stream().anyMatch(entry -> entry.toLowerCase().contains("snapshot"));
    }

    private void awaitClusterReady() {
        awaitReady(cluster);
    }

    private void awaitReady(DockerComposeCluster target) {
        awaitSuccess(eventually(vertx, () -> {
            List<String> endpoints = target.controllerEndpoints(NODE_COUNT);
            return endpoints.stream().allMatch(this::isHealthy) && hasLeader(endpoints);
        }, TIMEOUT), TIMEOUT.plusSeconds(5));
    }

    private boolean isHealthy(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            HttpResponse<io.vertx.core.buffer.Buffer> response = awaitSuccess(
                    webClient.get(uri.getPort(), uri.getHost(), "/health").send(), HTTP_TIMEOUT);
            return response.statusCode() == 200;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean hasLeader(List<String> endpoints) {
        return endpoints.stream().anyMatch(endpoint -> {
            try {
                URI uri = URI.create(endpoint);
                HttpResponse<io.vertx.core.buffer.Buffer> response = awaitSuccess(
                        webClient.get(uri.getPort(), uri.getHost(), "/raft/status").send(), HTTP_TIMEOUT);
                return response.statusCode() == 200
                        && Boolean.TRUE.equals(response.bodyAsJsonObject().getBoolean("isLeader"));
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    private void submitTransfer(String jobId) {
        submitTransferTo(cluster, jobId);
    }

    private void submitTransferTo(DockerComposeCluster target, String jobId) {
        JsonObject transfer = new JsonObject()
                .put("jobId", jobId)
                .put("sourceUri", "https://payments.example.test/" + jobId + ".dat")
                .put("destinationPath", "/tmp/" + jobId + ".dat")
                .put("totalBytes", 8_192L)
                .put("tenantId", TENANT_ID);

        awaitSuccess(eventually(vertx, () -> {
            for (String endpoint : target.controllerEndpoints(NODE_COUNT)) {
                URI uri = URI.create(endpoint);
                try {
                    HttpResponse<io.vertx.core.buffer.Buffer> response = awaitSuccess(
                            webClient.post(uri.getPort(), uri.getHost(), "/api/v1/transfers")
                                    .sendJsonObject(transfer), HTTP_TIMEOUT);
                    if (response.statusCode() == 201 || response.statusCode() == 200) {
                        return true;
                    }
                } catch (RuntimeException e) {
                    // try the next node: only the leader accepts writes
                }
            }
            return false;
        }, TIMEOUT), TIMEOUT.plusSeconds(5));
    }

    private void awaitVisibleOnEveryNode(String jobId) {
        awaitVisibleOnEveryNode(cluster, jobId);
    }

    private void awaitVisibleOnEveryNode(DockerComposeCluster target, String jobId) {
        awaitSuccess(eventually(vertx, () -> target.controllerEndpoints(NODE_COUNT).stream()
                .allMatch(endpoint -> findTransfer(endpoint, jobId) != null), TIMEOUT),
                TIMEOUT.plusSeconds(5));
    }

    private JsonObject getTransfer(String endpoint, String jobId) {
        JsonObject found = findTransfer(endpoint, jobId);
        if (found == null) {
            throw new AssertionError("Transfer " + jobId + " not readable from " + endpoint);
        }
        return found;
    }

    private JsonObject findTransfer(String endpoint, String jobId) {
        try {
            URI uri = URI.create(endpoint);
            HttpResponse<io.vertx.core.buffer.Buffer> response = awaitSuccess(
                    webClient.get(uri.getPort(), uri.getHost(), "/api/v1/transfers/" + jobId)
                            .putHeader("X-Tenant-Id", TENANT_ID)
                            .send(), HTTP_TIMEOUT);
            return response.statusCode() == 200 ? response.bodyAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
