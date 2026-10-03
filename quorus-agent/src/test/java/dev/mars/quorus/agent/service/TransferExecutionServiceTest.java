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

package dev.mars.quorus.agent.service;

import dev.mars.quorus.agent.config.AgentConfiguration;
import dev.mars.quorus.core.TransferRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static dev.mars.quorus.testing.TestResourceUtils.copyResource;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TransferExecutionServiceTest {

    @Test
    void refusesTransfersBeforeStartAndAfterShutdown() {
        TransferExecutionService service = new TransferExecutionService(createConfig());
        TransferRequest request = TransferRequest.builder().requestId("r")
                .sourceUri(URI.create("http://localhost:1/file")).destinationPath(Path.of("unused")).build();

        assertThrows(IllegalStateException.class, () -> service.executeTransfer(request), "not started");
        service.start();
        service.shutdown();

        assertThrows(IllegalStateException.class, () -> service.executeTransfer(request), "shut down");
        assertThrows(IllegalStateException.class, service::start, "a closed service cannot restart");
        assertDoesNotThrow(service::shutdown, "shutdown is idempotent");
    }

    @TempDir
    Path tls;

    @Test
    void productionAgentRejectsAnUngovernedAssignment() throws Exception {
        TransferExecutionService service = new TransferExecutionService(new AgentConfiguration.Builder()
                .agentId("test-agent").tenantId("test-tenant").controllerUrl("https://localhost:8080/api/v1")
                .securityProfile("production").allowInsecure(false).controllerTlsEnabled(true)
                .tlsCertificatePath(copyResource(getClass(), "/security/client-cert.pem", tls).toString())
                .tlsPrivateKeyPath(copyResource(getClass(), "/security/client-key.pem", tls).toString())
                .tlsTrustBundlePath(copyResource(getClass(), "/security/server-cert.pem", tls).toString())
                .build());
        service.start();
        try {
            JobPollingService.PendingJob ungoverned = new JobPollingService.PendingJob("a", "job", "test-agent",
                    "https://example.test/file", "/tmp/file", 1, "ungoverned");

            assertThrows(SecurityException.class, () -> service.executeTransfer(ungoverned));
        } finally {
            service.shutdown();
        }
    }

    private static AgentConfiguration createConfig() {
        return new AgentConfiguration.Builder()
                .securityProfile("development").allowInsecure(true).controllerTlsEnabled(false)
                .agentId("test-agent")
                .tenantId("test-tenant")
                .controllerUrl("http://localhost:8080/api/v1")
                .maxConcurrentTransfers(2)
                .heartbeatInterval(1000L)
                .build();
    }
}
