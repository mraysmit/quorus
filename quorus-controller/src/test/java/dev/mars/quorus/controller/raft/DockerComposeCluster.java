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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Directly drives {@code docker compose} for tests that need explicit control over the
 * container lifecycle.
 *
 * <p>Testcontainers' {@code ComposeContainer} owns start and stop, and always removes
 * volumes when it stops. The R1-1 container-recreation acceptance gate requires the
 * opposite: destroy the containers while <em>keeping</em> the named volumes, then recreate
 * containers from the same image against that surviving state. This helper exists for that
 * distinction and deliberately separates {@link #downKeepingVolumes()} from
 * {@link #downRemovingVolumes()}.</p>
 *
 * <p>Each instance uses its own compose project name so concurrent or repeated runs cannot
 * collide over container or volume names.</p>
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2026-09-07
 */
public final class DockerComposeCluster {

    private static final Logger logger = Logger.getLogger(DockerComposeCluster.class.getName());
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(3);

    private final File composeFile;
    private final String projectName;

    public DockerComposeCluster(String composeFilePath, String projectName) {
        this.composeFile = new File(composeFilePath);
        if (!composeFile.exists()) {
            throw new IllegalStateException(
                    "Compose file not found: " + composeFile.getAbsolutePath()
                    + " -- ensure the working directory is the quorus-controller module root");
        }
        this.projectName = projectName;
    }

    /** Creates and starts containers, leaving any existing named volumes intact. */
    public void up() {
        run("up", "-d", "--wait");
    }

    /**
     * Destroys containers and the network but preserves named volumes. This is the
     * container-recreation case: the workload is gone, the durable state is not.
     */
    public void downKeepingVolumes() {
        run("down", "--remove-orphans");
    }

    /** Destroys containers, network and named volumes. Used for test cleanup only. */
    public void downRemovingVolumes() {
        run("down", "--remove-orphans", "--volumes");
    }

    /**
     * Destroys and recreates a single service's container, keeping its named volume. The
     * remaining containers stay up, which is the rolling-replacement case: the cluster keeps
     * quorum while one member is replaced.
     */
    public void recreateService(String service) {
        run("rm", "--stop", "--force", service);
        run("up", "-d", "--wait", service);
    }

    /** Returns the host port bound to the given service's container port. */
    public int servicePort(String service, int containerPort) {
        String output = run("port", service, String.valueOf(containerPort)).trim();
        int separator = output.lastIndexOf(':');
        if (separator < 0) {
            throw new IllegalStateException(
                    "Could not parse published port for " + service + ": '" + output + "'");
        }
        return Integer.parseInt(output.substring(separator + 1).trim());
    }

    /** Returns host endpoints for {@code controller1..controllerN}. */
    public List<String> controllerEndpoints(int nodeCount) {
        List<String> endpoints = new ArrayList<>();
        for (int i = 1; i <= nodeCount; i++) {
            endpoints.add("http://localhost:" + servicePort("controller" + i, 8080));
        }
        return endpoints;
    }

    /**
     * Lists the files under {@code path} inside the named volume, without starting a
     * controller. Used to assert what durable artefacts actually exist on the volume
     * rather than inferring persistence from API behavior alone.
     */
    public static List<String> listVolumeContents(String volumeName, String path) {
        String output = execute(List.of(
                "docker", "run", "--rm",
                "-v", volumeName + ":/inspect",
                "busybox:1.36",
                "sh", "-c", "ls -1R /inspect" + path + " 2>/dev/null || true"));
        List<String> entries = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    /** Returns the qualified volume name compose creates for this project. */
    public String volumeName(String declaredVolume) {
        return projectName + "_" + declaredVolume;
    }

    private String run(String... composeArgs) {
        List<String> command = new ArrayList<>(List.of(
                "docker", "compose",
                "-f", composeFile.getAbsolutePath(),
                "-p", projectName));
        command.addAll(List.of(composeArgs));
        return execute(command);
    }

    private static String execute(List<String> command) {
        logger.info("Running: " + String.join(" ", command));
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    logger.fine("[compose] " + line);
                }
            }

            if (!process.waitFor(COMMAND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out: " + String.join(" ", command));
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "Command failed with exit code %d: %s%n%s",
                        exitCode, String.join(" ", command), output));
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted running: " + String.join(" ", command), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run: " + String.join(" ", command), e);
        }
    }
}
