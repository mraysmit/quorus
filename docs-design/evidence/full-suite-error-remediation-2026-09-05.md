# Full-suite error remediation — 2026-09-05

## Scope and acceptance

Fix the errors observed in `temp/full-suite-20260905.log` without suppressing failing
tests or changing production security defaults. Rerun affected boundaries, then the
complete clean Maven reactor with Docker and slow tests included. Preserve the
existing explicitly disabled network-partition test as a reported limitation.

## Behavioral red

- Original full suite: controller 576 tests, 19 errors, one skipped. Eighteen errors
  occurred in Docker cluster setup; the container log
  `temp/full-suite-docker-controller2-20260905.log` reports an unreadable HTTP
  certificate. The plaintext test Compose fixtures did not select development mode.
- `EnhancedInMemoryTransportTest.testCrashFailureMode` timed out before injecting
  the crash. Its log shows the same follower granting two candidates votes in term 1
  on different event-loop contexts; both candidates then act as leaders.
- Before changing `RaftNode`, `ConcurrentVoteBoundaryTest` failed both tests:
  incoming RPC work ran on a different context from startup, and all 32 overlapping
  durable vote requests were granted. The expected count is one. This used real
  RaftLog storage with sync enabled, not a mocking framework.
  Log: `temp/vote-boundary-red-20260905.log`.

## Changes and focused verification

- `RaftNode` retains its owning Vert.x context and uses it for lifecycle operations,
  RPC entry points, mutation dispatch and transport response callbacks. Vote decisions
  use the existing serialized mutation queue, including their metadata durability
  barrier, so a later candidate cannot observe an unfinished vote as unassigned.
- Both prebuilt Docker cluster fixtures explicitly select development mode and permit
  plaintext HTTP/Raft with request security disabled. These settings are restricted
  to the test fixtures; production defaults remain unchanged.
- The two new tests plus `EnhancedInMemoryTransportTest` and
  `FollowerRestartConsistencyTest` pass: 19 tests, zero failures/errors/skips.
  Log: `temp/vote-boundary-green-20260905.log`.
- The test Docker image was rebuilt from the modified source using the repository's
  build Compose file. Log: `temp/docker-fixture-rebuild-20260905.log`.
- The four previously failing Docker test classes pass with the existing skip.
  Log: `temp/docker-fixture-green-20260905.log`.

All Maven commands use JDK 25 and `2>&1 | Tee-Object -FilePath ...`. Focused tests
use `test -pl quorus-controller -am`, explicit `-Dtest` class lists, and
`-Dsurefire.failIfNoSpecifiedTests=false` for upstream modules. Docker tests also
use the POM-documented `-Dtest.excludedGroups=` override.

## Final verification

Command: `mvn.cmd --fail-at-end clean verify '-Dtest.excludedGroups='`.
Log: `temp/full-suite-fixed-20260905.log`. **BUILD SUCCESS** at
2026-09-05T15:46:52+08:00 (8 minutes 15 seconds). All seven reactor entries succeeded
and all five configured JaCoCo module gates passed. The six modules report 2,416
tests: **2,414 passed, zero failures/errors, two skipped**. Integration Examples
builds successfully and contains no tests. Core has 1,540 tests, Workflow 134,
Tenant 64, Controller 578 and Agent 100. See the accompanying JSON for source and
log SHA-256 hashes and per-module counts.

Both skips are existing explicit disables: `AdvancedNetworkTest.testDockerNetworkPartition`
requires network tooling/networks, and `NetworkPartitionTest.testMajorityPartition`
disables a no-op isolation scenario. The latter was previously masked by its class
initialization error; no test was newly disabled by this fix.

The pre-existing disabled network-partition test requires container network tooling
and networks. Several older Docker scenarios also log unsupported network-injection
operations; passing those tests is not proof that those faults were actually injected.
This work fixes the reported errors, not those broader test-coverage limitations or
the separate outstanding R1/R4–R6 handover acceptance gates.
