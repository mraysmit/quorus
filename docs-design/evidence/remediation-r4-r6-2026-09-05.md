# Remaining handover remediation — 2026-09-05

**Current status:** R4 and R5 implementation and R6 local final-source acceptance are
complete. The retained historical
sections below describe the sequence that led to the final implementation. See the
[R4 DNS evidence](r4-dns-remediation-2026-09-05.md) and
[R5 closure evidence](r5-closure-2026-09-05.md) for those results. The clean detached
R6 run at `b604505` passed 2,437 tests and five coverage gates; see
[R6 final acceptance](r6-final-acceptance-2026-09-05.md). R1 deployment durability
gates remain open.

**Subsequent RaftLog release update:** The missing 1.2.0 dependency recorded below was resolved by the newly implemented and published release from commit `1c5af80`. All 41 selected Quorus storage/snapshot/restart tests passed against it. Historical failures/counts below remain unchanged. R4, R5 and local R6 acceptance are now complete; R1 deployment acceptance remains open. See [release handover](raftlog-validation-handover-2026-09-05.md#implemented-capability-and-release--2026-09-05).

Historical base revision: `28f0530`. The implementation now includes the later R4 and
R5 slices. This record does not close the separate R1 deployment and power-loss gates.

RaftLog is the sister project at `../raftlog` relative to the Quorus repository root,
under the same parent directory (`C:\Users\mraysmit\dev\idea-projects\raftlog` on
this machine). Its `raftlog-core` module is built and installed through that separate
Maven reactor. The earlier claims about defects in RaftLog 1.1 and an already-existing
1.2.0 release were unsubstantiated remnants of the former integrated implementation.
RaftLog was subsequently changed in its own repository and 1.2.0 was published from
`1c5af80`. That later release is real evidence and does not validate the earlier claims.
The [validation handover](raftlog-validation-handover-2026-09-05.md) preserves both facts.

## R4.1 — Controller DNS leaves the event loop

Acceptance specified before implementation: both transfer submission and connection
validation must resolve DNS off the HTTP event loop, preserve tenant/egress policy and
address pinning, and produce their existing success responses. Slow resolution must
not block health requests. Resolution timeout and overload must fail closed with bounded
outstanding work; timed-out native lookups must retain their capacity slot until finished.

Test: `ControllerDnsBoundaryTest`, real HTTP routes, real policy engine, registry and
single-node Raft; a purpose-built DNS fixture is the only replaced external boundary.
Preparation added constructor injection of the existing synchronous HostResolver without
changing scheduling. Dependency resolution prevented behavioral red. The seam and test
are preserved in [an unapplied draft patch](r4-dns-boundary-draft-2026-09-05.patch), which
passes `git apply --check`; controller production Java remains at the baseline. Apply
the draft only when the exact dependency is available, then run red before scheduling
changes. No mocking framework or sleep synchronization is introduced.

Raw execution logs are retained under `temp/remediation-20260905`. The
[machine-readable manifest](remediation-r4-r6-2026-09-05.json) records timestamps,
log hashes, artifact hashes and 234 source/resource hashes checked against the isolated
checkout. No real credentials, keys or sensitive payloads were captured in new evidence;
protocol fixtures terminate before authentication or use empty synthetic credentials.

## R5.1 — Remote path fidelity
Acceptance before implementation: a root path scope permits descendants; literal filename characters (#, ?, spaces, percent, Unicode, and non-traversal dots) survive policy and HTTPS serialization. Traversal remains denied. RemotePathBoundaryTest enters a real TLS server and checks the received path. Existing traversal behavior is characterization; the six allowed paths are expected behavioral red. R4 is temporarily blocked by missing raftlog-core 1.2.0 (also absent from upstream refs).

## R5.2 — Controller entrypoint
Acceptance: execute the real Linux entrypoint with canonical-only, canonical plus legacy, legacy-only, and missing-cluster environments. Canonical values win; missing cluster fails. The initial container red revealed CRLF shell execution failure before precedence could be evaluated. Normalize the shell file to LF, then retain the separate precedence red. No deployed controller is started.

## R5.3 — TLS virtual host and redirect compatibility
Acceptance: pinned HTTPS on an ephemeral non-default port verifies the hostname and sends the correct Host authority. Ordinary HTTP downloads retain redirects. Tests exercise live HTTP/TLS servers; fixes must preserve governed redirect rejection.

## R5.4 — Agent defaults
Acceptance: an agent constructed with default builder security values stops after its first foreign assignment, matching the packaged threshold of one. Test enters through real agent startup and HTTP polling. No controller internals are invoked.

## R5.5 — FTPS default port
Acceptance: the controller/agent policy authorizes port 21 for a portless ftps URI, matching the adapter's documented explicit AUTH TLS default. Port 990 remains explicit implicit-TLS selection. A real port-21 protocol fixture observes AUTH TLS and rejects negotiation before authentication; no credentials or payload are transferred. This preserves existing adapter compatibility rather than silently changing portless clients to implicit TLS.

## R5.6 — Blocking adapters on Vert.x workers
Acceptance before implementation: reactive NFS performs a mounted-file copy, reactive SMB reaches its fail-closed mount-attestation policy, and reactive SFTP reaches a protocol peer. All direct blocking calls from event-loop threads must still fail. Tests use the public transferReactive entrypoint scheduled from a real Vert.x event loop. Worker threads retain event-loop contexts, so guard the actual thread rather than the context type. The FTPS precursor retained this same defect in r5-5-worker-red.log.

## Retained red/green results

Log names below are relative to `temp/remediation-20260905`. Each Maven command was
piped through `2>&1 | Tee-Object -FilePath <log>`; no failure was discarded or relabelled
as a passing gate. Patch identity is baseline `28f0530` plus the preceding slices in this
record and the named new tests. Final source hashes identify the reviewed implementation.

| Slice | Behavioral red | Focused green and refactor |
|---|---|---|
| R5.1 | `r5-1-red-scopes.log`: 15 cases, 2 assertion failures and 8 expected policy/path exceptions; 5 existing-behavior cases passed | `r5-1-green.log`: 27 pass; `r5-1-core-verify.log`: clean core 1,529 pass, coverage gate met |
| R5.2 | `r5-2-entrypoint-red.log`: real Linux shell rejects CRLF; after LF correction, `r5-2-entrypoint-precedence-red.log`: canonical-only cluster configuration rejected | `r5-2-entrypoint-green.log`: all four shell invocations pass; remaining precedence/fallback/rejection cases are regression, not individually preserved red |
| R5.3 | `r5-3-4-red.log`: real TLS rejects `localhost:port` as a peer hostname; ordinary HTTP download rejects 302 | `r5-3-green.log`: 61 pass; governed redirect rejection is additional regression coverage |
| R5.4 | `r5-3-4-red.log`: live agent remains running at mismatch 1/3; `r5-4-security-red.log`: two failures including default plaintext acceptance | `r5-4-green-implemented.log`: 49 pass; builder mapping is shared with `from(AgentConfig)` and duplicated default literals removed. Existing plaintext fixtures explicitly select development; the older three-mismatch test explicitly requests threshold 3 |
| R5.5 | `r5-5-red.log`: port policy denies the adapter's documented port 21; `r5-5-worker-red.log`: worker execution incorrectly rejected as an event-loop call | `r5-5-green-worker.log`: 52 pass, including real `AUTH TLS` exchange and cleanup before authentication |
| R5.6 | `r5-6-worker-red.log`: three reactive worker behaviors fail with the erroneous event-loop rejection; four direct event-loop rejection cases are characterization | `r5-6-worker-green.log`: 79 pass, including mounted-file copy, SMB assurance failure and SFTP peer connection |

Classification: R5.1/R5.3 are real HTTP/TLS serialization and failure-boundary tests;
R5.2 invokes the real deployment shell; R5.4 covers real agent startup/polling plus
configuration validation; R5.5/R5.6 cover public protocol boundaries and failure/cleanup.
Existing suite migrations and unchanged traversal/direct-blocking tests are regression
or characterization. They are not claimed as new historical TDD.

Commands identifying the principal stages:

```powershell
mvn.cmd -o test -pl quorus-core '-Dtest=RemotePathBoundaryTest'
mvn.cmd -o test -pl quorus-core '-Dtest=RemotePathBoundaryTest,GovernedConnectionSecurityTest,RuntimeProtocolSecurityTest'
docker run --rm --network none --mount 'type=bind,source=C:\Users\mraysmit\dev\idea-projects\quorus,target=/workspace,readonly' --entrypoint sh nginx:1.29.8-alpine /workspace/scripts/test-controller-entrypoint.sh
mvn.cmd -o test -pl quorus-agent -am '-Dtest=RemotePathBoundaryTest#pinnedHostnameSupportsTlsOnNonDefaultPort,HttpRedirectBoundaryTest,AgentDefaultIsolationBoundaryTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.failure.ignore=true'
mvn.cmd -o test -pl quorus-core '-Dtest=RemotePathBoundaryTest,HttpRedirectBoundaryTest,RuntimeProtocolSecurityTest,HttpTransferProtocolTest,HttpTransferProtocolUploadTest'
mvn.cmd -o test -pl quorus-agent -am '-Dtest=AgentTransportSecurityTest,AgentDefaultIsolationBoundaryTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn.cmd -o test -pl quorus-agent -am '-Dtest=AgentTransportSecurityTest,AgentDefaultIsolationBoundaryTest,AgentConfigTest,QuorusAgentTest,PreExecutionFailureIntegrationTest,ControllerWebClientFactoryTlsTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn.cmd -o test -pl quorus-core '-Dtest=FtpsDefaultPortBoundaryTest'
mvn.cmd -o test -pl quorus-core '-Dtest=FtpsDefaultPortBoundaryTest,FtpTransferProtocolTest,HttpRedirectBoundaryTest,RemotePathBoundaryTest,RuntimeProtocolSecurityTest,GovernedConnectionSecurityTest'
mvn.cmd -o test -pl quorus-core '-Dtest=BlockingProtocolWorkerBoundaryTest'
mvn.cmd -o test -pl quorus-core '-Dtest=BlockingProtocolWorkerBoundaryTest,FtpsDefaultPortBoundaryTest,NfsTransferProtocolTest,SmbTransferProtocolTest,SftpTransferProtocolTest'
```

The combined red command deliberately used `maven.test.failure.ignore=true` to reach
the agent after the core failures. Its `BUILD SUCCESS` footer is **not a green result**:
all three intended cases failed. No green or acceptance command uses that flag.

Other retained failures: the first path test had an incorrect fixture helper signature
(`r5-1-red.log`), fixed before behavioral red. The first agent-only invocation used an
obsolete installed core artifact (`r5-4-agent-red.log`); reactor execution corrected that
environment issue. `r5-4-green.log` still failed because an unavailable Python launcher
prevented the edit from running; the actual implementation is verified by the separately
named `r5-4-green-implemented.log`. `r5-5-green.log` timed out waiting for negotiation;
propagating the adapter failure exposed the separate worker guard defect before its fix.
None of these are hidden by the successful reruns.

## Available-module acceptance and R6 blocker

The isolated worktree is `temp/remediation-20260905/verify-worktree`, detached at
`28f0530` with the reviewed source changes copied and hash-checked. The final command:

```powershell
mvn.cmd -o clean verify -pl quorus-agent -am -f temp/remediation-20260905/verify-worktree/pom.xml 2>&1 | Tee-Object -FilePath temp/remediation-20260905/isolated-core-agent-verify.log
```

passes **1,774 tests** (core 1,540; workflow 134; agent 100), with **zero failures,
errors or skips**, and all three configured JaCoCo gates met. This includes configured
protocol/security tests and the existing Windows symlink case. It is not the final
seven-module reactor or a controller restart/power-loss acceptance result.

The isolated controller preflight (`mvn.cmd -o clean verify -pl quorus-controller -f
temp/remediation-20260905/verify-worktree/pom.xml`) fails before compilation because
`io.github.mraysmit:raftlog-core:1.2.0` is absent. A forced fresh lookup with
`mvn.cmd -U compile -pl quorus-controller -f temp/remediation-20260905/verify-worktree/pom.xml`
also failed after requesting both the POM and JAR from Maven Central
(`r6-controller-online-refresh.log`); this is not merely a cached negative result.
local and upstream-advertised raftlog HEAD is `7a3bd3a`/1.1.0. The earlier evidence's
`db59859` object is not in the local raftlog repository. Neither that claimed revision
nor a 1.2.0 release has been substantiated. Validate the sister project's actual
contracts and reconcile Quorus's dependency/API requirements; do not fabricate a release
or treat the historical replay assertions as established 1.1.0 defects.

`scripts/verify-phase0-docs.ps1` passes for 30 active documents. `git diff --check` with
intentional Markdown hard-break whitespace excluded passes. No commit, deployment,
storage migration, pruning or production acceptance was performed.

## Final R5 handover disposition

| Item | Current disposition / required next work |
|---|---|
| Original findings 1–3 and 8 | Previously remediated; preserve historical evidence and include in final controller regression |
| Finding 4 | Entrypoint fixed and exercised in Linux container |
| Finding 5 | Complete: obsolete public convenience constructors were removed from `ProtocolFactory`, `SimpleTransferEngine`, `HttpApiServer`, and `NfsTransferProtocol`; supported construction paths remain covered by focused regression |
| Finding 6 | Complete: resource loading, environment precedence and typed lookup mechanics are consolidated in `LayeredProperties`; application-specific validation and logging remain at their boundaries |
| Finding 7 | Builder default duplication removed; live default threshold and production TLS rejection verified |
| Section 7 DNS | Complete under R4: bounded worker resolution, overload, timeout, authority recheck, pins and HTTP responsiveness retain behavioral red and focused/full green evidence |
| Section 7 tenant registry / pre-execution reports | R2/R3 committed in `28f0530`; controller-dependent final regression remains required |
| Section 7 root scopes / literal paths / TLS host / redirects / FTPS | Addressed by R5.1, R5.3 and R5.5; worker scheduling additionally corrected by R5.5/R5.6 |
| Route probe default ports | Complete: route probes use the shared effective-port contract; portless endpoints reach their documented protocol defaults |
| Partial trust-policy updates | Complete: omitted CA IDs, host keys, peer pins and minimum TLS settings retain their stored values; supplied fields still replace them |
| Legacy credential-bearing replay and scripts | Complete for R5: new credential-bearing submissions remain rejected; legacy protobuf commands are redacted and made terminal before state/snapshot persistence; the Docker script uses credential-free HTTP fixtures and the current request/response contract |
| Controller/agent codecs | Complete: `ServiceConnectionJsonCodec` is the shared redacted contract, including case-insensitive enums and controller defaults |
| Security-event retention and full-map listing | Complete for R5: reads are tenant-scoped, bounded to 1–1000 rows and cursor-paged without copying all system metadata. Authoritative events are not automatically pruned; archive, legal hold and retention policy remain Phase 9 work |
| Per-transfer clients / repeated trust-store loads | Complete for R5: governed clients remain isolated and are closed after each transfer; immutable trust-manager policies use a bounded 64-entry cache, and rotation selects a distinct manager |
| R1 deployment durability | Container-recreation, selected production filesystem and power-loss gates remain open |
| R6 | Complete for local final-source acceptance at `b604505`: the clean detached reactor passed 2,437 tests, zero failures/errors, two existing skips and five coverage gates; R1 deployment durability remains open |

R5 completion does not waive R1 container-recreation, selected production-filesystem,
or machine power-loss acceptance.
