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

import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Shared Docker cluster containers for integration tests.
 *
 * <p>Uses the singleton pattern to build the Docker image once and share
 * running 3-node and 5-node clusters across all test classes. This avoids
 * redundant Docker image builds and container startups.</p>
 *
 * <h3>Performance impact:</h3>
 * <ul>
 *   <li>Image built ONCE via {@code docker compose build} (not per test class)</li>
 *   <li>Clusters started ONCE and reused across DockerRaftClusterTest,
 *       ConfigurableRaftClusterTest, AdvancedNetworkTest, NetworkPartitionTest</li>
 *   <li>Pre-built compose files use {@code image:} — no build context transfer on start</li>
 * </ul>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025-08-20
 */
public final class SharedDockerCluster {

    private static final Logger logger = Logger.getLogger(SharedDockerCluster.class.getName());

    private static volatile boolean imageBuilt = false;
    private static ComposeContainer threeNodeCluster;
    private static ComposeContainer fiveNodeCluster;

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(SharedDockerCluster::shutdown, "SharedDockerCluster-shutdown"));
    }

    private SharedDockerCluster() {}

    /**
     * Returns the shared 3-node cluster, building the image and starting containers on first call.
     */
    public static synchronized ComposeContainer getThreeNodeCluster() {
        if (threeNodeCluster == null) {
            ensureImageBuilt();

            logger.info("Starting shared 3-node cluster...");
            threeNodeCluster = new ComposeContainer(
                    new File("src/test/resources/docker-compose-3node-prebuilt.yml"))
                    .withExposedService("controller1", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .withExposedService("controller2", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .withExposedService("controller3", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .waitingFor("controller1", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .waitingFor("controller2", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .waitingFor("controller3", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3));

            threeNodeCluster.start();
            logger.info("Shared 3-node cluster started successfully");
        }
        return threeNodeCluster;
    }

    /**
     * Returns the shared 5-node cluster, building the image and starting containers on first call.
     */
    public static synchronized ComposeContainer getFiveNodeCluster() {
        if (fiveNodeCluster == null) {
            ensureImageBuilt();

            logger.info("Starting shared 5-node cluster...");
            fiveNodeCluster = new ComposeContainer(
                    new File("src/test/resources/docker-compose-5node-prebuilt.yml"))
                    .withExposedService("controller1", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .withExposedService("controller2", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .withExposedService("controller3", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .withExposedService("controller4", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .withExposedService("controller5", 8080, Wait.forHttp("/health").forStatusCode(200))
                    .waitingFor("controller1", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .waitingFor("controller2", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .waitingFor("controller3", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .waitingFor("controller4", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .waitingFor("controller5", Wait.forLogMessage(".*Starting Raft node.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3));

            fiveNodeCluster.start();
            logger.info("Shared 5-node cluster started successfully");
        }
        return fiveNodeCluster;
    }

    /**
     * Returns HTTP endpoints for nodes in the given cluster.
     */
    public static List<String> getNodeEndpoints(ComposeContainer cluster, int nodeCount) {
        List<String> endpoints = new ArrayList<>();
        for (int i = 1; i <= nodeCount; i++) {
            Integer port = cluster.getServicePort("controller" + i, 8080);
            endpoints.add("http://localhost:" + port);
        }
        return endpoints;
    }

    /**
     * Builds the quorus-controller:test Docker image using the build compose file.
     * This reuses the same build mechanism as the existing compose files (including
     * BuildKit cache mounts and the m2cache additional context), but builds only once
     * per JVM rather than once per test class.
     */
    private static synchronized void ensureImageBuilt() {
        if (imageBuilt) return;

        File buildComposeFile = new File("src/test/resources/docker-compose-build-image.yml");
        if (!buildComposeFile.exists()) {
            throw new RuntimeException(
                    "Build compose file not found: " + buildComposeFile.getAbsolutePath()
                    + " — ensure working directory is the quorus-controller module root");
        }

        logger.info("Building Docker image via: " + buildComposeFile.getAbsolutePath());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose",
                    "-f", buildComposeFile.getAbsolutePath(),
                    "build");
            pb.environment().put("DOCKER_BUILDKIT", "1");
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Drain output to prevent blocking
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.fine("[Docker Build] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(
                        "Docker image build failed with exit code: " + exitCode);
            }

            imageBuilt = true;
            logger.info("Docker image built successfully: quorus-controller:test");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Docker image", e);
        }
    }

    private static void shutdown() {
        logger.info("Shutting down shared Docker clusters...");
        if (threeNodeCluster != null) {
            try { threeNodeCluster.stop(); } catch (Exception e) { /* ignore */ }
        }
        if (fiveNodeCluster != null) {
            try { fiveNodeCluster.stop(); } catch (Exception e) { /* ignore */ }
        }
    }
}
