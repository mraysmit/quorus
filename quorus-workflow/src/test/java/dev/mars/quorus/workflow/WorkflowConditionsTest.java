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

import dev.mars.quorus.testing.ExpectsError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow {@code condition} expressions (decision ENG-Q1, delivery item ENG-14), exercised through
 * the caller's entry point: YAML text parsed by {@link YamlWorkflowDefinitionParser} and run by
 * {@link SimpleWorkflowEngine}. The grammar is {@code success(name)}, {@code failure(name)},
 * {@code file_exists('path')}, {@code not}, {@code and}, {@code or} and parentheses; anything else
 * fails validation. A false condition skips its group or transfer.
 */
@Timeout(value = 30, unit = SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class WorkflowConditionsTest {

    private final YamlWorkflowDefinitionParser parser = new YamlWorkflowDefinitionParser();
    private TestTransferEngine transferEngine;
    private SimpleWorkflowEngine workflowEngine;

    @BeforeEach
    void setUp() {
        transferEngine = new TestTransferEngine();
        transferEngine.simulateSuccess();
        workflowEngine = new SimpleWorkflowEngine(transferEngine);
    }

    // ---------------------------------------------------------------- validation

    @Test
    void aConditionOutsideTheGrammarFailsValidation() throws Exception {
        for (String condition : List.of("success(up", "maybe(up)", "success(up) xor failure(up)",
                "success()", "file_exists(/data/x)", "success(up) and", "SUCCESS(up)")) {
            ValidationResult result = validate(workflow(Map.of(),
                    group("up", List.of(), null, false, transfer("fetch", null)),
                    group("down", List.of("up"), condition, false, transfer("load", null))));

            assertFalse(result.isValid(), () -> "'" + condition + "' must be refused");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldPath().equals("spec.transferGroups[1].condition")),
                    () -> "expected an error at the group's condition for '" + condition + "': " + result.getErrors());
        }
    }

    @Test
    void aConditionNamingNothingInTheWorkflowFailsValidation() throws Exception {
        ValidationResult result = validate(workflow(Map.of(),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("up"), null, false, transfer("load", "success(nowhere)"))));

        assertError(result, "spec.transferGroups[1].transfers[0].condition", "nowhere");
    }

    @Test
    void aConditionOnATransferInTheSameGroupFailsValidation() throws Exception {
        ValidationResult result = validate(workflow(Map.of(),
                group("only", List.of(), null, false,
                        transfer("archive", null), transfer("cleanup", "success(archive)"))));

        assertError(result, "spec.transferGroups[0].transfers[1].condition", "archive");
    }

    @Test
    void aConditionOnAGroupThatIsNotADependencyFailsValidation() throws Exception {
        ValidationResult result = validate(workflow(Map.of(),
                group("left", List.of(), null, false, transfer("a", null)),
                group("right", List.of(), "success(left)", false, transfer("b", null))));

        assertError(result, "spec.transferGroups[1].condition", "left");
    }

    @Test
    void aNameThatIsBothAGroupAndATransferFailsValidation() throws Exception {
        ValidationResult result = validate(workflow(Map.of(),
                group("fetch", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("fetch"), "success(fetch)", false, transfer("load", null))));

        assertError(result, "spec.transferGroups[1].condition", "fetch");
    }

    @Test
    void aVariableReferenceOutsideAFileExistsPathFailsValidation() throws Exception {
        ValidationResult result = validate(workflow(Map.of("target", "up"),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("up"), "success({{target}})", false, transfer("load", null))));

        assertError(result, "spec.transferGroups[1].condition", "variable");
    }

    @Test
    void failureOfAGroupThatStopsTheWorkflowWhenItFailsIsAWarning() throws Exception {
        ValidationResult result = validate(workflow(Map.of(),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("up"), "failure(up)", false, transfer("load", null))));

        assertTrue(result.isValid(), () -> "a warning, not an error: " + result.getErrors());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.getFieldPath().equals("spec.transferGroups[1].condition")
                        && w.getMessage().contains("continueOnError")),
                () -> "expected a continueOnError warning: " + result.getWarnings());
    }

    @Test
    void conditionsOnUpstreamGroupsAndTheirTransfersAreValid() throws Exception {
        ValidationResult result = validate(workflow(Map.of("dir", "/data"),
                group("first", List.of(), null, true, transfer("fetch", null)),
                group("second", List.of("first"), "success(first)", false, transfer("load", null)),
                group("third", List.of("second"), "success(fetch) and not failure(second)", false,
                        transfer("publish", "file_exists('{{dir}}/ready') or (success(load) and success(first))"))));

        assertTrue(result.isValid(), () -> "a transitive upstream group or transfer may be named: " + result.getErrors());
    }

    // ---------------------------------------------------------------- execution

    @Test
    void aFalseGroupConditionSkipsTheGroupAndTheWorkflowStillCompletes() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of(),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("up"), "failure(up)", false, transfer("load", null))));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus(), "a skipped group is not a failure");
        assertEquals(WorkflowStatus.SKIPPED, group(execution, "down").getStatus());
        assertEquals(Set.of("fetch"), requested());
    }

    @Test
    void aSkippedGroupsDependentsStillRun() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of(),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("up"), "failure(up)", false, transfer("load", null)),
                group("after", List.of("down"), null, false, transfer("publish", null))));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(WorkflowStatus.COMPLETED, group(execution, "after").getStatus());
        assertEquals(Set.of("fetch", "publish"), requested());
    }

    @Test
    void neitherSuccessNorFailureIsTrueOfASkippedGroup() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of(),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("maybe", List.of("up"), "failure(up)", true, transfer("load", null)),
                group("after", List.of("maybe"), null, false,
                        transfer("whenMaybeSucceeded", "success(maybe)"),
                        transfer("whenMaybeFailed", "failure(maybe)"),
                        transfer("whenLoadFailed", "failure(load)"),
                        transfer("always", null))));

        assertEquals(WorkflowStatus.SKIPPED, group(execution, "maybe").getStatus());
        assertEquals(Set.of("fetch", "always"), requested());
        assertEquals(Set.of("whenMaybeSucceeded", "whenMaybeFailed", "whenLoadFailed"),
                group(execution, "after").getSkippedTransfers());
    }

    @Test
    void transferConditionsChooseWhichTransfersOfAGroupRun() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of(),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("up"), null, false,
                        transfer("whenUpSucceeded", "success(up)"),
                        transfer("whenFetchSucceeded", "success(fetch)"),
                        transfer("whenUpFailed", "failure(up)"))));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(Set.of("fetch", "whenUpSucceeded", "whenFetchSucceeded"), requested());
        WorkflowExecution.GroupExecution down = group(execution, "down");
        assertEquals(Set.of("whenUpFailed"), down.getSkippedTransfers());
        assertFalse(down.getTransferResults().containsKey("whenUpFailed"), "a skipped transfer has no result");
    }

    @Test
    @ExpectsError("An upstream transfer fails so that failure(...) conditions become true")
    void failureIsTrueAfterAnUpstreamFailureThatTheWorkflowContinuesPast() throws Exception {
        transferEngine.failSourcesContaining("/fetch");

        WorkflowExecution execution = run(workflow(Map.of(),
                group("up", List.of(), null, true, transfer("fetch", null)),
                group("down", List.of("up"), null, false,
                        transfer("recover", "failure(up) and failure(fetch)"),
                        transfer("proceed", "success(up)"))));

        assertEquals(WorkflowStatus.FAILED, execution.getStatus(), "the upstream group still failed");
        assertEquals(Set.of("fetch", "recover"), requested());
        assertEquals(Set.of("proceed"), group(execution, "down").getSkippedTransfers());
    }

    @Test
    void fileExistsChecksTheResolvedPath(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("present.txt"), "x");

        WorkflowExecution execution = run(workflow(Map.of("dir", slashes(dir)),
                group("only", List.of(), null, false,
                        transfer("whenPresent", "file_exists('{{dir}}/present.txt')"),
                        transfer("whenAbsent", "file_exists(\"{{dir}}/absent.txt\")"))));

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        assertEquals(Set.of("whenPresent"), requested());
        assertEquals(Set.of("whenAbsent"), group(execution, "only").getSkippedTransfers());
    }

    @Test
    void notBindsTighterThanAndWhichBindsTighterThanOr(@TempDir Path dir) throws Exception {
        WorkflowExecution execution = run(workflow(Map.of("dir", slashes(dir)),
                group("up", List.of(), null, false, transfer("fetch", null)),
                group("down", List.of("up"), null, false,
                        // (not failure(up)) and false  -> false; not (failure(up) and false) would be true
                        transfer("notThenAnd", "not failure(up) and file_exists('{{dir}}/absent')"),
                        // true or (false and false) -> true; (true or false) and false would be false
                        transfer("andThenOr", "success(up) or file_exists('{{dir}}/absent') and failure(up)"),
                        transfer("grouped", "not (failure(up) or file_exists('{{dir}}/absent'))"))));

        assertEquals(Set.of("fetch", "andThenOr", "grouped"), requested());
        assertEquals(Set.of("notThenAnd"), group(execution, "down").getSkippedTransfers());
    }

    @Test
    void aVirtualRunEvaluatesConditionsToo(@TempDir Path dir) throws Exception {
        WorkflowExecution execution = workflowEngine.virtualRun(parser.parseFromString(workflow(Map.of("dir", slashes(dir)),
                group("only", List.of(), null, false,
                        transfer("always", null),
                        transfer("whenAbsentFileExists", "file_exists('{{dir}}/absent')")))), context());

        assertEquals(WorkflowStatus.COMPLETED, execution.getStatus());
        WorkflowExecution.GroupExecution only = group(execution, "only");
        assertEquals(Set.of("always"), only.getTransferResults().keySet());
        assertEquals(Set.of("whenAbsentFileExists"), only.getSkippedTransfers());
    }

    @Test
    @ExpectsError("A condition whose resolved path breaks the grammar fails its transfer")
    void aConditionThatCannotBeEvaluatedFailsItsTransferWithoutStartingIt() throws Exception {
        WorkflowExecution execution = run(workflow(Map.of("dir", "/tmp/it's"),
                group("only", List.of(), null, true,
                        transfer("fine", null),
                        transfer("broken", "file_exists('{{dir}}/x')"))));

        assertEquals(WorkflowStatus.FAILED, execution.getStatus());
        assertEquals(Set.of("fine"), requested(), "the transfer with the broken condition must not start");
        assertFalse(group(execution, "only").getTransferResults().get("broken").isSuccessful());
    }

    // ---------------------------------------------------------------- helpers

    private ValidationResult validate(String yaml) throws Exception {
        return parser.validate(parser.parseFromString(yaml));
    }

    private WorkflowExecution run(String yaml) throws Exception {
        return workflowEngine.execute(parser.parseFromString(yaml), context());
    }

    private static ExecutionContext context() {
        return ExecutionContext.builder()
                .executionId("conditions-" + System.nanoTime())
                .mode(ExecutionContext.ExecutionMode.NORMAL)
                .userId("test-user")
                .build();
    }

    /** The names of the transfers the transfer engine was asked to run (the source path is the name). */
    private Set<String> requested() {
        return transferEngine.getRequests().stream()
                .map(request -> request.getSourceUri().getPath().substring(1))
                .collect(Collectors.toSet());
    }

    private static WorkflowExecution.GroupExecution group(WorkflowExecution execution, String name) {
        return execution.getGroupExecutions().stream()
                .filter(group -> group.getGroupName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no execution of group " + name + ": " + execution.getGroupExecutions()));
    }

    private static void assertError(ValidationResult result, String fieldPath, String mention) {
        assertFalse(result.isValid(), () -> "expected a validation error at " + fieldPath);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldPath().equals(fieldPath)
                        && e.getMessage().contains(mention)),
                () -> "expected an error at " + fieldPath + " mentioning '" + mention + "': " + result.getErrors());
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String transfer(String name, String condition) {
        StringBuilder yaml = new StringBuilder()
                .append("        - name: ").append(name).append('\n')
                .append("          source: \"http://example.com/").append(name).append("\"\n")
                .append("          destination: \"/tmp/").append(name).append("\"\n")
                .append("          protocol: http\n");
        if (condition != null) {
            yaml.append("          condition: '").append(condition.replace("'", "''")).append("'\n");
        }
        return yaml.toString();
    }

    private static String group(String name, List<String> dependsOn, String condition, boolean continueOnError,
                                String... transfers) {
        StringBuilder yaml = new StringBuilder()
                .append("    - name: ").append(name).append('\n')
                .append("      continueOnError: ").append(continueOnError).append('\n');
        if (!dependsOn.isEmpty()) {
            yaml.append("      dependsOn:\n");
            dependsOn.forEach(dependency -> yaml.append("        - ").append(dependency).append('\n'));
        }
        if (condition != null) {
            yaml.append("      condition: '").append(condition.replace("'", "''")).append("'\n");
        }
        yaml.append("      transfers:\n");
        for (String transfer : transfers) {
            yaml.append(transfer);
        }
        return yaml.toString();
    }

    private static String workflow(Map<String, String> variables, String... groups) {
        StringBuilder yaml = new StringBuilder("""
                metadata:
                  name: "conditions-test-workflow"
                  version: "1.0.0"
                  description: "Workflow conditions test"
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
                    parallelism: 2
                    timeout: 60s
                  transferGroups:
                """);
        for (String group : groups) {
            yaml.append(group);
        }
        return yaml.toString();
    }
}
