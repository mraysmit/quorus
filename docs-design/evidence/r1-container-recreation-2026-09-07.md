<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# R1-1 Container-Recreation Durability — Evidence Record

**Date:** 2026-09-07
**Author:** Mark Ray-Smith — Cityline Ltd
**License:** Apache 2.0
**Slice:** `R1-1` — container-recreation acceptance
**Revision:** `804e11d` — committed evidence, tests, durable fixture, plan/register reconciliation, and `LeaderGuardHandlerTest` correction
**Register item:** [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER.md) §3
**Classification:** external-path behavioral + **retrospective characterization** — see §2

---

## 1. Acceptance statement

Written before implementation:

> Committed authoritative state, durable snapshots and their recovery coordinates survive
> destruction and recreation of the controller containers, when the shipped image is
> configured with its Raft storage path on a persistent named volume. Recovery must work
> after the WAL has been compacted, so that recovery is proven from a durable snapshot rather
> than from a full log replay.

## 2. Classification and honest process record

**This slice did not find a product defect. It supplies missing acceptance evidence.**

Both failures in the retained red stage (§4) were incorrect assertions in the new test, not
missing product behavior. The durable snapshot and recovery behavior already worked, having
been delivered by the earlier R1 code remediation. Under the
[§6.1 mandatory TDD protocol](../task/QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md), existing
implementation with no preserved red stage can only receive **retrospective
characterization**, and that is what the two recovery assertions are. They are recorded as
characterization here and are not represented as historical TDD.

The fourth test, `removingVolumesLosesStateProvingTheGateIsNotVacuous`, is a genuine control
rather than characterization: it fails if the fixture ever stops exercising volume-backed
durability, and it therefore protects the value of the other three.

No production source was changed by this slice. The only non-test change is the promotion of
`SharedDockerCluster.ensureImageBuilt()` to a public `buildImageIfAbsent()` so a test managing
its own container lifecycle can reuse the shared image without starting a cluster it will not
use.

## 3. What this gate exercises that existing tests do not

`ThreeControllerDurableRestartTest` restarts in-process controllers against the same temporary
directory. That proves recovery across a **process** lifetime. It never destroys a container
filesystem, never exercises the shipped image's storage configuration, and never proves that
the deployed volume mount is what actually holds authoritative state.

A material gap was found in the existing fixtures during this work: the test compose file
`docker-compose-3node-prebuilt.yml` declares **no volumes and no `QUORUS_RAFT_STORAGE_PATH`**.
Every containerised test before this slice therefore ran with Raft state on the container's
ephemeral layer. No existing test could have detected a container-level durability regression.

## 4. Red stage — retained

Command:

```
mvn.cmd -pl quorus-controller -Djacoco.skip=true "-Dtest.excludedGroups=" \
  -Dtest=ContainerRecreationDurabilityTest -DfailIfNoTests=false test
```

Result: `Tests run: 2, Failures: 2, Errors: 0, Skipped: 0` — BUILD FAILURE.

| Failure | Assertion | Diagnosis |
|---|---|---|
| `committedTransferSurvivesContainerRecreation` | `Tenant ownership must survive container recreation ==> expected: <regulated-bank-a> but was: <null>` | **Incorrect assertion.** `TransferHandler.handleGet()` does not echo `tenantId`; tenant is enforced server-side via `SecurityContext.trustedTenant`, and this plaintext fixture has no authenticated identity to enforce against. Assertion replaced with recovered-content assertions. |
| `recoveryUsesDurableSnapshotAfterCompaction` | `Condition was not satisfied within PT1M` | **Incorrect assumption.** The test looked for a snapshot on `controller1`'s volume. Only the leader takes snapshots and compacts; an up-to-date follower never receives an `InstallSnapshot` and writes no snapshot file. The test now discovers the leader through `/raft/status`. |

Both failures were investigated against source and against a live cluster before either
assertion was changed, so that a wrong assertion could not be mistaken for a product defect
and quietly "fixed" in the product.

## 5. Direct observation of durable state

A probe cluster confirmed the actual on-volume behavior. After eight transfers committed
through the leader with `QUORUS_RAFT_SNAPSHOT_THRESHOLD=3`:

```
/inspect/raft:
-rw-r--r-- 1 1001 1001    27 meta.dat
-rw-r--r-- 1 1001 1001     0 raft.lock
-rw-r--r-- 1 1001 1001     0 raft.log          <-- WAL compacted to zero bytes
-rw-r--r-- 1 1001 1001  7232 snapshot.dat      <-- durable snapshot present
-rw-r--r-- 1 1001 1001    12 snapshot.required
```

After `docker compose down --remove-orphans` (containers destroyed, volumes retained), the
same five artifacts remained on the volume with no controller process alive. After recreating
the containers, all three controllers served the committed transfer:

```
controller1: {"jobId":"probe-5","sourceUri":"https://payments.example.test/p5.dat",...,"status":"PENDING"}
controller2: {"jobId":"probe-5",...}
controller3: {"jobId":"probe-5",...}
```

This is recovery from a durable snapshot with a fully compacted WAL, which is the specific
behavior R1 replaced and the specific risk the register records.

## 6. Green stage

Command as in §4. Result: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS,
58.65 s.

| Test | Classification | What it asserts |
|---|---|---|
| `committedTransferSurvivesContainerRecreation` | characterization | Full-cluster destroy and recreate; every node returns the committed `jobId`, `sourceUri`, `totalBytes` and `status` — recovered content, not key presence |
| `recoveryUsesDurableSnapshotAfterCompaction` | characterization | A snapshot artifact exists on the leader's volume before recreation, survives with no container alive, and the transfer is readable after recreation |
| `singleNodeRecreationRejoinsWithoutDataLoss` | characterization | Rolling replacement: one follower recreated while the other two hold quorum; the recreated node serves the committed transfer after rejoining |
| `removingVolumesLosesStateProvingTheGateIsNotVacuous` | control | With `--volumes` the transfer is gone; proves the other assertions are actually volume-backed |

## 7. Regression

```
mvn.cmd -pl quorus-controller "-Dtest.excludedGroups=" clean verify
```

Result: **601 tests, 0 failures, 0 errors, 2 skipped. BUILD SUCCESS. JaCoCo check passed.**

The two skips are the pre-existing explicitly disabled network tests, the same two recorded by
the R6 acceptance:

- `AdvancedNetworkTest` — network partition simulation requires `iptables`/`tc` in the image
  and pre-created Docker networks;
- `NetworkPartitionTest` — node isolation is not implemented; `simulateNodeIsolation()` is a
  no-op.

## 8. Artifacts

| Artifact | Path |
|---|---|
| Acceptance test | `quorus-controller/src/test/java/dev/mars/quorus/controller/raft/ContainerRecreationDurabilityTest.java` |
| Compose lifecycle driver | `quorus-controller/src/test/java/dev/mars/quorus/controller/raft/DockerComposeCluster.java` |
| Durable-volume fixture | `quorus-controller/src/test/resources/docker-compose-3node-durable.yml` |
| Shared image helper change | `quorus-controller/src/test/java/dev/mars/quorus/controller/raft/SharedDockerCluster.java` |

No request bodies, credentials, keys or sensitive payloads were captured in this evidence. The
fixture uses synthetic hostnames under `example.test` and carries no secrets.

## 9. Environment

| Property | Value |
|---|---|
| Docker engine | 29.7.2 (Docker Desktop, Windows) |
| Host OS | Windows 11 Pro N 10.0.26340 |
| JDK | 25 |
| Storage backend | `raftlog-core` (external RaftLog 1.2.0); configuration accepts only `raftlog` |
| Fixture snapshot policy | threshold 3, check interval 2000 ms, fsync enabled |

## 10. Limitations — what this does NOT close

1. **`R1-3` machine power-loss remains open.** A graceful container stop flushes differently
   from an unclean host power cut. This gate says nothing about torn writes or lost fsync.
2. **`R1-2` production-filesystem acceptance is only partly addressed.** These runs used
   Docker Desktop on Windows, where containers execute inside a virtual machine with its own
   page cache. Docker is now confirmed as an intended production target, so the deployment
   *shape* is representative, but the storage class and host kernel are not. R1-2 needs a
   repeat on the Linux engine and storage class actually intended for production.
3. **Snapshot corruption is not covered here.** The register's R1 description includes
   corruption and retained-tail cases; those are exercised in-process by the existing R1 suite
   and are not repeated at the container boundary by this slice.
4. **The default containerised test fixture is still non-durable.** Only the new
   `docker-compose-3node-durable.yml` mounts volumes. Other Docker tests continue to run on
   ephemeral storage, which is appropriate for their purpose but means they carry no
   durability meaning.

## 11. Disposition

`R1-1` is **closed for the Docker container-recreation shape on the engine recorded in §9**,
with the classification in §2 and the limitations in §10. `R1-2` and `R1-3` remain open and
continue to block the enterprise release claim.

---

## 12. Remediation of findings — 2026-09-07

The three issues surfaced by this slice were remediated in a follow-up pass. All are test and
fixture concerns; no production source changed.

### 12.1 Non-durable containerised test fixtures

Every containerised fixture now writes Raft state to a named volume at `/app/data/raft`, the
same path the deployed image uses, with `QUORUS_RAFT_STORAGE_PATH` set explicitly:

| Fixture | Services given volumes |
|---|---|
| `docker-compose-3node-prebuilt.yml` | controller1–3 |
| `docker-compose-5node-prebuilt.yml` | controller1–5 |
| `docker-compose-5node-test.yml` | controller1–5 |
| `docker-compose-test.yml` | controller1–3 |

This is behavior-neutral for the existing tests: none of them restarts a container, and the
shared cluster is never stopped mid-run, so the same state simply lives on a volume instead of
the container's ephemeral layer. The benefit is that containerised tests now exercise the
deployed storage shape, so a regression in the image's storage configuration would surface
broadly rather than nowhere. Testcontainers removes these volumes when it stops the cluster,
so each run still starts clean.

Verified directly rather than inferred — after starting the amended 3-node prebuilt fixture,
the volume holds the real state:

```
/inspect/raft:
meta.dat
raft.lock
raft.log
```

The obsolete `version:` key was also removed from these files; Compose has been ignoring it and
emitting a warning on every invocation.

### 12.2 `OBS-08` — orphaned `TransferMetrics`

`quorus-core/src/main/java/dev/mars/quorus/monitoring/TransferMetrics.java` and its
`TransferMetricsTest` are deleted. The class had no remaining production caller after v2.5
removed it from `SimpleTransferEngine`; only its own test referenced it.
`NetworkTopologyService.getTransferMetrics()` is an unrelated name collision and was left
untouched. `quorus-core` clean verify passes 1,517 tests with zero failures, errors or skips
and meets its JaCoCo gate.

### 12.3 `LeaderGuardHandlerTest` intermittent 15-second fixture timeout

Root-caused rather than retried. `@BeforeAll` discarded all three `RaftNode.start()` futures
and immediately began polling for a leader. Startup was therefore still in flight while the
election wait ran, and `guard-node-1` — deliberately configured with a 400 ms election timeout
against peers at 30 s and 45 s — could campaign before its peers' in-memory transports were
registered, burning elections against an unreachable cluster.

The fix awaits the start futures, and starts the two slow-election followers before the
fast-election node so it cannot campaign into an empty cluster. Fixture setup now completes in
about 1.5 s. Three consecutive runs pass 7 tests each with no timeout; the previous behavior
was a non-reproducing timeout at the 15-second boundary.

### 12.4 Systemic finding NOT remediated — unawaited `start()` in tests

The same discarded-future pattern appears in roughly twenty other controller test classes
(`HttpApiServerHealthTest`, `JobAssignmentHandlerTest`, `StateTransitionIntegrationTest`,
`GrpcRaftServerTest`, `RaftFailureTest` and others). Only `LeaderGuardHandlerTest` has observed
failure evidence, and a blind sweep of twenty startup paths carries more regression risk than
the latent flakiness it would remove. It is recorded as register item `OBS-15` for a deliberate,
verified pass rather than silently changed here.

### 12.5 Regression after remediation

```
mvn.cmd -pl quorus-controller "-Dtest.excludedGroups=" clean verify
```

**601 tests, 0 failures, 0 errors, 2 skipped. BUILD SUCCESS. JaCoCo check passed.** The two
skips remain the pre-existing explicitly disabled network tests. `quorus-core` clean verify
passes 1,517 tests with zero failures, errors or skips.
