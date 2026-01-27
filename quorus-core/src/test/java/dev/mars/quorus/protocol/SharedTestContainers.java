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

import java.io.File;
import java.time.Duration;
import java.util.logging.Logger;

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

    private static final Logger logger = Logger.getLogger(SharedTestContainers.class.getName());

    // SFTP container (atmoz/sftp:alpine)
    private static volatile ComposeContainer sftpContainer;
    private static final Object SFTP_LOCK = new Object();

    // FTP container (fauria/vsftpd)
    private static volatile ComposeContainer ftpContainer;
    private static final Object FTP_LOCK = new Object();

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
                    logger.info("Shared SFTP container started at " + getSftpHost() + ":" + getSftpPort());
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

    /**
     * Gets the shared FTP container, starting it if necessary.
     * 
     * @return the FTP ComposeContainer
     */
    public static ComposeContainer getFtpContainer() {
        if (ftpContainer == null) {
            synchronized (FTP_LOCK) {
                if (ftpContainer == null) {
                    logger.info("Starting shared FTP container...");
                    ftpContainer = new ComposeContainer(
                            new File("src/test/resources/docker-compose-ftp-test.yml"))
                            .withExposedService("ftp", 21, Wait.forListeningPort())
                            .withStartupTimeout(Duration.ofMinutes(2));
                    ftpContainer.start();
                    registerShutdownHook();
                    logger.info("Shared FTP container started at " + getFtpHost() + ":" + getFtpPort());
                }
            }
        }
        return ftpContainer;
    }

    /**
     * Gets the FTP host, starting the container if necessary.
     */
    public static String getFtpHost() {
        return getFtpContainer().getServiceHost("ftp", 21);
    }

    /**
     * Gets the FTP port, starting the container if necessary.
     */
    public static int getFtpPort() {
        return getFtpContainer().getServicePort("ftp", 21);
    }

    /**
     * Checks if the FTP container is running.
     */
    public static boolean isFtpRunning() {
        return ftpContainer != null;
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
            logger.warning("Docker is not available: " + e.getMessage());
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
                logger.warning("Error stopping SFTP container: " + e.getMessage());
            }
        }
        if (ftpContainer != null) {
            try {
                logger.info("Stopping shared FTP container...");
                ftpContainer.stop();
            } catch (Exception e) {
                logger.warning("Error stopping FTP container: " + e.getMessage());
            }
        }
    }
}
