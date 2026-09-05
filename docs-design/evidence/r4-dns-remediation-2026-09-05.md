# R4 — bounded controller DNS authorization

Base revision: `f8fb15eb5292a20ef16d71d641731d614f9f2c52`. Changes are uncommitted.

## Acceptance and implementation

Transfer submission and service-connection validation must resolve DNS off the HTTP
event loop, keep health requests responsive, share bounded outstanding work, and fail
closed on overload/deadline. Timed-out native lookups retain capacity until completion.
Preserve tenant/egress policy, exact address pins and secret authority; discard late
approval and recheck registry state after asynchronous resolution.

`ControllerConnectionAuthorizer` is shared by both handlers in `HttpApiServer`.
It executes the existing policy engine and resolver on unordered Vert.x workers;
registry access, response processing and Raft submission remain on the request context.
An atomic admission counter bounds queued plus executing work. Defaults are eight
outstanding operations and a 5,000 ms deadline, configurable with positive
`quorus.http.dns.max-concurrent` / `quorus.http.dns.timeout-ms` values.
Overload returns HTTP 503; deadline expiry returns HTTP 504. Deadline includes worker
queue time; expired queued work skips native resolution. Actual worker completion,
including lookup failure, releases capacity. The existing Vert.x instance owns worker
lifecycle; this change adds no independent executor or DNS cache.

After DNS succeeds, changed/removed connections and changed/unusable transfer secret
references return HTTP 409 before submission. Timed-out outcomes cannot create a
transfer, record validation approval or initiate the optional route probe.
If the unchanged secret expires during resolution, transfer denial first persists its
`EXPIRED` transition and `Q-SECRET-EXPIRED` audit event.

## TDD evidence

All test commands used JDK 25, `mvn.cmd test -pl quorus-controller -am`, a focused
`-Dtest` selection, `-Dsurefire.failIfNoSpecifiedTests=false`, and
`2>&1 | Tee-Object -FilePath ...`. No mocking framework was used. Tests use real HTTP
routes, policy/registry/Raft implementations and purpose-built resolver fixtures.
Semaphores model a blocked native resolver; observation uses Vert.x futures/timers,
without sleep-based test synchronization.

| Stage | Behavioral red | Green |
|---|---|---|
| HTTP event-loop isolation | `temp/r4-dns-eventloop-red.log`: two assertions fail because resolution runs on the event loop | `temp/r4-dns-eventloop-green.log`: two pass after worker dispatch |
| Shared capacity and deadlines | `temp/r4-dns-capacity-red.log`: four fail; second requests are admitted instead of 503, and client deadlines expire without a server timeout response | `temp/r4-dns-capacity-green.log`: six pass, including retained slots and suppression of late work |
| Registry changes during DNS | `temp/r4-dns-revocation-red.log`: three fail with 200/201 after connection/secret removal | `temp/r4-dns-revocation-green.log`: 13 pass after registry rechecks and added characterization |
| Secret expires during DNS | `temp/r4-dns-expiry-red.log`: expected EXPIRED but observed ACTIVE after denial | `temp/r4-dns-final-focused.log`: expiry status and audit assertions pass after the durable transition is restored |

Fifteen final boundary cases comprise ten with retained behavioral red and five
characterization/regression cases: mixed DNS allow/deny answers (two), failure cleanup
with persisted address pins, expired queued work, and concurrent use of available
capacity. Existing tests verify connection HTTP behavior, tenant registry isolation,
security boundaries and route probing. The final combined focused run passes **47 tests,
zero failures/errors/skips** (`temp/r4-dns-final-focused.log`). The preceding 46-case
run is retained in `temp/r4-dns-focused-regression.log`.

The revocation-green command also listed two nonmatching class selectors; only the
13 boundary cases ran in that command. The subsequent focused regression uses the
actual existing class names; the final run accounts for all 47 results explicitly.

## Final verification

Docker image rebuilt with the repository's build Compose file; log:
`temp/r4-dns-final-docker-rebuild.log` (supersedes the pre-expiry-fix build in
`temp/r4-dns-docker-rebuild.log`). Final image SHA-256:
`5d9f143c2b2805f88738ff06e142e707e3ff07f89bdde65031c38fb1039ead59`.

The JDK 25 command `mvn.cmd --fail-at-end clean verify '-Dtest.excludedGroups='`
completed at **2026-09-05T18:55:26+08:00**, with **BUILD SUCCESS** across all seven
reactor entries and all five configured JaCoCo gates. Docker and slow groups were
enabled. Log: `temp/r4-dns-full-verify.log`.

| Module | Reported tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Core | 1,540 | 0 | 0 | 0 |
| Workflow | 134 | 0 | 0 | 0 |
| Tenant | 64 | 0 | 0 | 0 |
| Controller | 593 | 0 | 0 | 2 |
| Agent | 100 | 0 | 0 | 0 |
| Integration Examples | 0 | 0 | 0 | 0 |
| **Total** | **2,431** | **0** | **0** | **2** |

Thus **2,429 tests passed**. Counts come from final Surefire XML reports, not
interleaved per-class console summaries. The 15-test increase over the preceding
2,416-test run is the new DNS boundary suite. The two existing disables remain
`AdvancedNetworkTest.testDockerNetworkPartition` (missing network tools/networks)
and `NetworkPartitionTest.testMajorityPartition` (isolation helper is a no-op).
No new test was disabled. Passing network tests that log unsupported fault-injection
commands do not establish those network-fault capabilities.

The [machine-readable record](r4-dns-remediation-2026-09-05.json) retains source and
log hashes, command selections and module counts. The 30-document verification
script and Markdown-aware whitespace check also pass. R4 implementation is complete;
this is a working-tree result based on `f8fb15e`, not isolated final-revision acceptance.

The retained original R4 draft patch is historical preparation and has now been
applied and superseded by this implementation. Earlier missing-dependency failures
were not behavioral red. R5, R6 final-revision/isolated-checkout acceptance and R1
deployment/power-loss gates are separate; this record does not close them.
