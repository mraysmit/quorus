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

package dev.mars.quorus.protocol;

import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared test containers that are reused across multiple integration test classes.
 * 
 * <p>This class implements the "singleton container" pattern recommended by Testcontainers
 * for sharing containers across test classes within the same JVM. This significantly
 * improves test execution time by avoiding container startup/shutdown for each test class.
 * 
 * <p>Containers are started lazily on first access and remain running until JVM shutdown.
 * A shutdown hook ensures proper cleanup.
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * class MyIntegrationTest {
 *     @BeforeAll
 *     static void startContainers() {
 *         SharedTestContainers.getSftpContainer(); // Starts if not already running
 *     }
 *     
 *     @Test
 *     void testSomething() {
 *         String host = SharedTestContainers.getSftpHost();
 *         int port = SharedTestContainers.getSftpPort();
 *         // ... use SFTP server
 *     }
 * }
 * }</pre>
 * 
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @version 1.0
 * @since 2025
 */
public final class SharedTestContainers {

    private static final Logger logger = LoggerFactory.getLogger(SharedTestContainers.class);

    // SFTP container (atmoz/sftp:alpine)
    private static volatile ComposeContainer sftpContainer;
    private static final Object SFTP_LOCK = new Object();

    // FTP container (delfer/alpine-ftp-server)
    private static volatile ComposeContainer ftpContainer;
    private static final Object FTP_LOCK = new Object();

    // FTPS container (delfer/alpine-ftp-server with TLS)
    private static volatile ComposeContainer ftpsContainer;
    private static final Object FTPS_LOCK = new Object();

    // Track if shutdown hook is registered
    private static volatile boolean shutdownHookRegistered = false;
    private static final Object HOOK_LOCK = new Object();

    private SharedTestContainers() {
        // Utility class
    }

    /**
     * Gets the shared SFTP container, starting it if necessary.
     * 
     * @return the SFTP ComposeContainer
     */
    public static ComposeContainer getSftpContainer() {
        if (sftpContainer == null) {
            synchronized (SFTP_LOCK) {
                if (sftpContainer == null) {
                    logger.info("Starting shared SFTP container...");

                    sftpContainer = new ComposeContainer(
                            new File("src/test/resources/docker-compose-sftp-abort-test.yml"))
                            .withExposedService("sftp", 22, Wait.forListeningPort())
                            .withStartupTimeout(Duration.ofMinutes(2));
                    sftpContainer.start();
                    registerShutdownHook();
                    logger.info("Shared SFTP container started at {}:{}", getSftpHost(), getSftpPort());
                }
            }
        }
        return sftpContainer;
    }

    /**
     * Gets the SFTP host, starting the container if necessary.
     */
    public static String getSftpHost() {
        return getSftpContainer().getServiceHost("sftp", 22);
    }

    /**
     * Gets the SFTP port, starting the container if necessary.
     */
    public static int getSftpPort() {
        return getSftpContainer().getServicePort("sftp", 22);
    }

    /**
     * Checks if the SFTP container is running.
     */
    public static boolean isSftpRunning() {
        return sftpContainer != null;
    }

    // Direct ports for FTP avoid the Testcontainers socat proxy which changes
    // source IP, triggering vsftpd's "425 Security: Bad IP connecting" error
    // when the passive data connection comes from a different IP than control.
    // Allocate them per run so concurrent builds do not contend for fixed ports.
    private static volatile int ftpControlPort;
    private static volatile int ftpDataPort;

    /**
     * Gets the shared FTP container, starting it if necessary.
     * 
     * <p>Uses <b>direct port mappings</b> for control and passive data to bypass
     * Testcontainers' socat proxy. The host ports are selected per test run. This is necessary
     * because vsftpd verifies that data connections come from the same IP as
     * the control connection, and the socat proxy changes the source IP.
     * 
     * @return the FTP ComposeContainer
     */
    public static ComposeContainer getFtpContainer() {
        if (ftpContainer == null) {
            synchronized (FTP_LOCK) {
                if (ftpContainer == null) {
                    logger.info("Starting shared FTP container...");

                    int[] ports = findAvailablePorts(2);
                    ftpControlPort = ports[0];
                    ftpDataPort = ports[1];
                    ComposeContainer container = new ComposeContainer(
                            new File("src/test/resources/docker-compose-ftp-test.yml"))
                            .withEnv("FTP_CONTROL_PORT", Integer.toString(ftpControlPort))
                            .withEnv("FTP_DATA_PORT", Integer.toString(ftpDataPort))
                            .withStartupTimeout(Duration.ofMinutes(2));
                    try {
                        container.start();
                        // The FTP 220 handshake is the authoritative readiness boundary. Docker
                        // health can lag or report unhealthy even when the mapped protocol is ready.
                        waitForFtpReady("localhost", ftpControlPort, Duration.ofMinutes(1));
                        ftpContainer = container;
                    } catch (RuntimeException e) {
                        container.stop();
                        throw e;
                    }
                    registerShutdownHook();
                    logger.info("Shared FTP container started at localhost:{} (PASV data port {})",
                            ftpControlPort, ftpDataPort);
                }
            }
        }
        return ftpContainer;
    }

    /**
     * Gets the FTP host. Returns localhost since FTP uses a direct port mapping.
     */
    public static String getFtpHost() {
        getFtpContainer(); // Ensure container is started
        return "localhost";
    }

    /**
     * Gets the dynamically selected FTP control port.
     */
    public static int getFtpPort() {
        getFtpContainer(); // Ensure container is started
        return ftpControlPort;
    }

    /**
     * Checks if the FTP container is running.
     */
    public static boolean isFtpRunning() {
        return ftpContainer != null;
    }

    // Direct ports for FTPS avoid the Testcontainers socat proxy which breaks
    // TLS session reuse between control and data channels in FTP passive mode.
    // Allocate them per run so concurrent builds do not contend for fixed ports.
    private static volatile int ftpsControlPort;
    private static volatile int ftpsDataPort;

    /**
     * Gets the shared FTPS container, starting it if necessary.
     * Uses delfer/alpine-ftp-server with TLS enabled (explicit FTPS via AUTH TLS).
     * The container auto-generates a self-signed certificate.
     * 
     * <p>Unlike SFTP containers, the FTPS container uses <b>direct port mappings</b>
     * selected per test run instead of Testcontainers' socat proxy. This is necessary
     * because FTPS passive mode requires TLS session
     * reuse between the control and data channels, and the socat proxy breaks this
     * by introducing a different endpoint for the control channel.
     * 
     * @return the FTPS ComposeContainer
     */
    public static ComposeContainer getFtpsContainer() {
        if (ftpsContainer == null) {
            synchronized (FTPS_LOCK) {
                if (ftpsContainer == null) {
                    logger.info("Starting shared FTPS container...");

                    int[] ports = findAvailablePorts(2);
                    ftpsControlPort = ports[0];
                    ftpsDataPort = ports[1];
                    ComposeContainer container = new ComposeContainer(
                            new File("src/test/resources/docker-compose-ftps-test.yml"))
                            .withBuild(true)
                            .withEnv("FTPS_CONTROL_PORT", Integer.toString(ftpsControlPort))
                            .withEnv("FTPS_DATA_PORT", Integer.toString(ftpsDataPort))
                            .withStartupTimeout(Duration.ofMinutes(3));
                    try {
                        container.start();
                        // Use the real FTP protocol boundary rather than Docker health metadata.
                        waitForFtpReady("localhost", ftpsControlPort, Duration.ofMinutes(2));
                        ftpsContainer = container;
                    } catch (RuntimeException e) {
                        container.stop();
                        throw e;
                    }
                    registerShutdownHook();
                    logger.info("Shared FTPS container started at localhost:{} (PASV data port {})",
                            ftpsControlPort, ftpsDataPort);
                }
            }
        }
        return ftpsContainer;
    }

    /**
     * Gets the FTPS host. Returns localhost since FTPS uses a direct port mapping.
     */
    public static String getFtpsHost() {
        getFtpsContainer(); // Ensure container is started
        return "localhost";
    }

    /**
     * Gets the dynamically selected FTPS control port.
     */
    public static int getFtpsPort() {
        getFtpsContainer(); // Ensure container is started
        return ftpsControlPort;
    }

    /**
     * Checks if the FTPS container is running.
     */
    public static boolean isFtpsRunning() {
        return ftpsContainer != null;
    }

    /**
     * Waits for an FTP service to become ready on the given host:port.
     * Unlike a simple TCP port check, this verifies the FTP daemon responds
     * with a 220 welcome message. This is essential because pure-ftpd listens
     * on the port before completing DH parameter generation / TLS initialisation,
     * and will close connections that arrive before it's ready.
     */
    private static void waitForFtpReady(String host, int port, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        logger.info("Waiting for FTP service at {}:{} (timeout: {})...", host, port, timeout);
        int attempt = 0;
        int consecutiveSuccesses = 0;
        final int requiredSuccesses = 3;
        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(5000);
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream()));
                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                        socket.getOutputStream());
                String welcome = reader.readLine();
                if (welcome != null && welcome.startsWith("220")) {
                    consecutiveSuccesses++;
                    // Send QUIT to cleanly close the FTP session
                    writer.write("QUIT\r\n");
                    writer.flush();
                    if (consecutiveSuccesses >= requiredSuccesses) {
                        logger.info("FTP service ready at {}:{} after {} attempts ({} consecutive 220 replies): {}",
                                host, port, attempt, requiredSuccesses, welcome);
                        return;
                    }
                    logger.debug("FTP readiness warm-up (attempt {}): got 220 ({} of {} required)",
                            attempt, consecutiveSuccesses, requiredSuccesses);
                } else {
                    consecutiveSuccesses = 0;
                    logger.debug("FTP not ready yet (attempt {}): {}", attempt, welcome);
                }
            } catch (java.io.IOException e) {
                consecutiveSuccesses = 0;
                logger.debug("FTP not ready yet (attempt {}): {}", attempt, e.getMessage());
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for FTP at " + host + ":" + port, ie);
            }
        }
        throw new RuntimeException("Timed out waiting for FTP service at " + host + ":" + port + " after " + timeout);
    }

    /**
     * Reserves distinct ephemeral ports long enough to collect their numbers, then
     * releases them immediately before Docker Compose binds them.
     */
    private static int[] findAvailablePorts(int count) {
        List<ServerSocket> sockets = new ArrayList<>(count);
        try {
            int[] ports = new int[count];
            for (int index = 0; index < count; index++) {
                ServerSocket socket = new ServerSocket(0);
                socket.setReuseAddress(false);
                sockets.add(socket);
                ports[index] = socket.getLocalPort();
            }
            return ports;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate ports for an FTP test container", e);
        } finally {
            for (ServerSocket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.debug("Unable to close temporary port-allocation socket", e);
                }
            }
        }
    }

    /**
     * Checks if Docker is available for running containers.
     * Use this in test assumptions to skip tests when Docker is unavailable.
     */
    public static boolean isDockerAvailable() {
        try {
            // Try to create a minimal container to check Docker availability
            org.testcontainers.DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            logger.warn("Docker is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Registers a JVM shutdown hook to stop all containers.
     */
    private static void registerShutdownHook() {
        if (!shutdownHookRegistered) {
            synchronized (HOOK_LOCK) {
                if (!shutdownHookRegistered) {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        logger.info("Stopping shared test containers...");
                        stopAllContainers();
                    }, "SharedTestContainers-Shutdown"));
                    shutdownHookRegistered = true;
                }
            }
        }
    }

    /**
     * Stops all running containers. Called by shutdown hook.
     */
    private static void stopAllContainers() {
        if (sftpContainer != null) {
            try {
                logger.info("Stopping shared SFTP container...");
                sftpContainer.stop();
            } catch (Exception e) {
                logger.warn("Error stopping SFTP container: {}", e.getMessage());
            }
        }
        if (ftpContainer != null) {
            try {
                logger.info("Stopping shared FTP container...");
                ftpContainer.stop();
            } catch (Exception e) {
                logger.warn("Error stopping FTP container: {}", e.getMessage());
            }
        }
        if (ftpsContainer != null) {
            try {
                logger.info("Stopping shared FTPS container...");
                ftpsContainer.stop();
            } catch (Exception e) {
                logger.warn("Error stopping FTPS container: {}", e.getMessage());
            }
        }
    }
}
