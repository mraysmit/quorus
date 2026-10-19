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

package dev.mars.quorus.workflow;

import com.sun.net.httpserver.HttpServer;
import dev.mars.quorus.core.TransferRequest;
import dev.mars.quorus.testing.ExpectsError;
import dev.mars.quorus.transfer.SimpleTransferEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow transfer {@code options} (decision DR-Q1, delivery item ENG-26), exercised through the
 * caller's entry point: YAML text parsed by {@link YamlWorkflowDefinitionParser} and run by
 * {@link SimpleWorkflowEngine}. The defined options are {@code checksum} and {@code expectedSize};
 * any other key fails validation, and a value that is invalid once resolved fails its transfer
 * before it starts.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class WorkflowTransferOptionsTest {

    private static final String SHA256_OF_HELLO =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"; // sha256("hello")

    private final YamlWorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
    private TestTransferEngine transferEngine;
    private SimpleWorkflowEngine workflowEngine;

    @BeforeEach
    void setUp() {
        transferEngine = new TestTransferEngine();
        transferEngine.simulateSuccess();
        workflowEngine = new SimpleWorkflowEngine(transferEngine);
    }

    @Test
    void aChecksumOptionReachesTheTransferRequest() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of(), "checksum: \"" + SHA256_OF_HELLO.toUpperCase() + "\""));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        TransferRequest request = onlyRequest();
        assertEquals(SHA256_OF_HELLO, request.getExpectedChecksum(),
                "the checksum reaches the request, normalised to the lower-case hex the adapters compute");
    }

    @Test
    void anExpectedSizeOptionReachesTheTransferRequest() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of(), "expectedSize: 1048576"));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(1_048_576L, onlyRequest().getExpectedSize());
    }

    @Test
    void optionValuesAreResolvedFromVariablesBeforeTheyReachTheRequest() throws Exception {
        WorkflowExecution execution = run(workflow(
                Map.of("sum", SHA256_OF_HELLO, "size", "5"),
                "checksum: \"{{sum}}\"", "expectedSize: \"{{size}}\""));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        TransferRequest request = onlyRequest();
        assertEquals(SHA256_OF_HELLO, request.getExpectedChecksum());
        assertEquals(5L, request.getExpectedSize());
    }

    @Test
    void anUnknownOptionKeyFailsValidation() throws Exception {
        WorkflowDefinition definition = parser.parseFromString(workflow(Map.of(), "timeout: 30s"));

        ValidationResult result = parser.validate(definition);

        assertFalse(result.isValid(), "an option nothing honours must not be accepted");
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("timeout")
                        && e.getFieldPath().contains("options")),
                () -> "expected an error naming the unknown option: " + result.getErrors());
    }

    @Test
    @ExpectsError("A workflow with an unknown option is refused before any transfer starts")
    void aWorkflowWithAnUnknownOptionRunsNoTransfer() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of(), "chunkSize: 8192"));

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertEquals(0, transferEngine.getAttempts());
    }

    @Test
    void aMalformedChecksumFailsValidation() throws Exception {
        ValidationResult result = parser.validate(parser.parseFromString(
                workflow(Map.of(), "checksum: \"not-a-sha256\"")));

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldPath().contains("checksum")),
                () -> "expected a checksum error: " + result.getErrors());
    }

    @Test
    void aNegativeOrFractionalExpectedSizeFailsValidation() throws Exception {
        for (String value : new String[] {"-1", "10.5", "\"lots\""}) {
            ValidationResult result = parser.validate(parser.parseFromString(
                    workflow(Map.of(), "expectedSize: " + value)));

            assertFalse(result.isValid(), () -> "expectedSize " + value + " must be refused");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldPath().contains("expectedSize")),
                    () -> "expected an expectedSize error for " + value + ": " + result.getErrors());
        }
    }

    @Test
    @ExpectsError("An option that resolves to an invalid value fails its transfer before it starts")
    void anOptionThatResolvesToAnInvalidValueFailsItsTransferWithoutStartingIt() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of("size", "lots"), "expectedSize: \"{{size}}\""));

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertEquals(0, transferEngine.getAttempts(), "the transfer engine must not be asked to run it");
    }

    @Test
    void aNestedVariableReferenceIsResolvedInTheTransferRequest() throws Exception {
        WorkflowExecution execution = run(workflowWithDestination(
                Map.of("outputRoot", "/data/out", "outputDir", "{{outputRoot}}/daily"),
                "{{outputDir}}/report.csv"));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(Path.of("/data/out/daily/report.csv").toUri(), onlyRequest().getDestinationUri());
    }

    @Test
    @ExpectsError("A cyclic variable reference fails the workflow instead of being left literal")
    void aCyclicVariableReferenceFailsTheWorkflow() throws Exception {
        WorkflowExecution execution = run(workflowWithDestination(
                Map.of("a", "{{b}}", "b", "{{a}}"), "/tmp/{{a}}"));

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertEquals(0, transferEngine.getAttempts());
    }

    /**
     * The checksum reaches a real adapter: with the real transfer engine and HTTP adapter against a
     * local server, the content's checksum succeeds and a different checksum fails the transfer.
     */
    @Test
    @ExpectsError("The adapter rejects a download whose checksum does not match the workflow's option")
    void theChecksumOptionIsVerifiedByTheRealAdapter(@TempDir Path tempDir) throws Exception {
        byte[] content = "hello quorus".getBytes(StandardCharsets.UTF_8);
        String actualSum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/file", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(content);
            }
        });
        server.start();
        SimpleTransferEngine realEngine = new SimpleTransferEngine(2, 0, 10);
        try {
            SimpleWorkflowEngine engine = new SimpleWorkflowEngine(realEngine);
            String source = "http://127.0.0.1:" + server.getAddress().getPort() + "/file";

            Path good = tempDir.resolve("good.txt");
            WorkflowExecution matching = engine.execute(parser.parseFromString(
                    workflowWith(Map.of(), source, slashes(good), "checksum: \"" + actualSum + "\"")), context());
            assertEquals(WorkflowStatus.COMPLETED, matching.getStatus(), "the matching checksum must pass");
            assertArrayEquals(content, Files.readAllBytes(good));

            Path bad = tempDir.resolve("bad.txt");
            WorkflowExecution mismatching = engine.execute(parser.parseFromString(
                    workflowWith(Map.of(), source, slashes(bad), "checksum: \"" + SHA256_OF_HELLO + "\"")), context());
            assertEquals(WorkflowStatus.FAILED, mismatching.getStatus(),
                    "a checksum the content does not have must fail the transfer");
        } finally {
            realEngine.shutdown(java.time.Duration.ofSeconds(5));
            server.stop(0);
        }
    }

    private WorkflowExecution run(String yaml) throws Exception {
        return workflowEngine.execute(parser.parseFromString(yaml), context());
    }

    private static ExecutionContext context() {
        return ExecutionContext.builder()
                .executionId("options-" + System.nanoTime())
                .mode(ExecutionContext.ExecutionMode.NORMAL)
                .userId("test-user")
                .build();
    }

    private TransferRequest onlyRequest() {
        assertEquals(1, transferEngine.getRequests().size(), "expected exactly one transfer request");
        return transferEngine.getRequests().getFirst();
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String workflow(Map<String, String> variables, String... optionLines) {
        return workflowWith(variables, "http://example.com/file.txt", "/tmp/file.txt", optionLines);
    }

    private static String workflowWithDestination(Map<String, String> variables, String destination) {
        return workflowWith(variables, "http://example.com/file.txt", destination);
    }

    private static String workflowWith(Map<String, String> variables, String source, String destination,
                                       String... optionLines) {
        StringBuilder yaml = new StringBuilder("""
                metadata:
                  name: "options-test-workflow"
                  version: "1.0.0"
                  description: "Workflow transfer options test"
                  type: "validation-test-workflow"
                  author: "test@quorus.dev"
                  created: "2026-10-18"
                  tags: ["test"]
                spec:
                """);
        if (!variables.isEmpty()) {
            yaml.append("  variables:\n");
            variables.forEach((name, value) -> yaml.append("    ").append(name).append(": \"").append(value).append("\"\n"));
        }
        yaml.append("""
                  execution:
                    parallelism: 1
                    timeout: 60s
                  transferGroups:
                    - name: group1
                      transfers:
                        - name: transfer1
                """);
        yaml.append("          source: \"").append(source).append("\"\n");
        yaml.append("          destination: \"").append(destination).append("\"\n");
        yaml.append("          protocol: http\n");
        if (optionLines.length > 0) {
            yaml.append("          options:\n");
            for (String line : optionLines) {
                yaml.append("            ").append(line).append('\n');
            }
        }
        return yaml.toString();
    }
}
