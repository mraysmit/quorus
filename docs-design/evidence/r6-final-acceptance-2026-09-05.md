# R6 final acceptance evidence — 2026-09-05

R6 local source-tree acceptance is complete for revision
`b604505d1cbf2ff62c7967f358b4aabe25b4f2ff`. The revision was checked out in the
clean detached worktree `temp/r6-final-worktree-2`, its controller image was rebuilt
from that tree, and the complete Maven reactor was run with the repository's Docker
and slow test groups enabled.

R5 is also complete. Its behavioral red/green stages and 254-test focused regression
are recorded in [R5 closure evidence](r5-closure-2026-09-05.md).

## Definitive isolated verification

The run used `JAVA_HOME=C:\Users\mraysmit\.jdks\openjdk-25` and this exact command:

```powershell
mvn.cmd --fail-at-end clean verify '-Dtest.excludedGroups=' 2>&1 | Tee-Object -FilePath temp/r6-final-isolated-full.log
```

It completed at `2026-09-05T20:24:28+08:00` with **BUILD SUCCESS** in 8 minutes
7 seconds. All seven reactor entries succeeded and all five configured JaCoCo module
gates passed. Surefire reports contain **2,437 tests, zero failures, zero errors and
two skips**:

| Module | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Core | 1,541 | 0 | 0 | 0 |
| Workflow | 134 | 0 | 0 | 0 |
| Tenant | 64 | 0 | 0 | 0 |
| Controller | 597 | 0 | 0 | 2 |
| Agent | 101 | 0 | 0 | 0 |
| Integration Examples | 0 | 0 | 0 | 0 |

The full log SHA-256 is
`F6010C257AAC98C1EEEAB25EF4C9A9E7DFBA4CE28E079F65ED5E31068D6EF919`.
The isolated Docker build log SHA-256 is
`9A0D0EBDF32E81A48AFBBE30AF8A4869073685C192B540F0794F209CB27544C5`;
it records image `sha256:a887656498f6f941eaecb8f7e5ac833865bbe9136a9ee13cec92737e4e35972c`
tagged as `quorus-controller:test`.

The command covers the configured protocol, security, serialization, migration,
snapshot, restart, Docker-cluster and slow Raft lanes. The two skips are the existing
explicitly disabled cases
`AdvancedNetworkTest#testDockerNetworkPartition` and
`NetworkPartitionTest#testMajorityPartition`. Enabled advanced-network cases log
expected failures when the image lacks `iptables` or `tc`; their passing result is not
evidence that those operating-system faults were injected.

## Isolated-run defects and correction

The first detached run at `0fefecbc2278893b3668b924c71d7aff86b08b38` retained two
controller failures in `temp/r6-final-worktree/temp/r6-isolated-full.log` (SHA-256
`BDEE7391F8DF33027B7DEFBE5758F8949E068415B8F9647C9D76F612D02EAB80`):

- `ConcurrentVoteBoundaryTest#incomingVotesUseTheNodeContext` read an observation
  after an already-completed future and could see `null` even when the durable vote
  update ran on the node's owning context.
- `RaftLogClusterIntegrationTest#testVoteMetadataPersistence` failed with
  `OverlappingFileLockException`. `RaftNode.stop()` skipped storage closure when
  `transport.stop()` failed, leaving the sister RaftLog project's WAL lock held.

`RaftNode.stop()` now always composes storage closure after transport shutdown, while
retaining the transport failure. The context test uses a small forwarding observer
around the real storage boundary and asserts the metadata update itself. No mocking
framework is used. The focused slow lane passed all six context/restart cases; its log
SHA-256 is `9391A3354E9DF3865AB007FF43890959F83F88BEAD4CD72C745D0DEA17D2FCF7`.
Commit `b604505d1cbf2ff62c7967f358b4aabe25b4f2ff` contains this correction and is the
revision used by the definitive isolated run.

## Scope and remaining release gates

RaftLog is the sister project at `../raftlog`, under the same parent directory as
Quorus. Its later 1.2.0 release was produced from RaftLog revision `1c5af80`; the older
claims about RaftLog 1.1 defects and an already-existing 1.2.0 were unsubstantiated and
remain identified as such in the handover.

R6 establishes local final-source acceptance. It does not close R1's
container-recreation, selected production-filesystem, or machine power-loss gates.
Those deployment and durability checks still block an enterprise release claim.
