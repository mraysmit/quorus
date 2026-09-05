# RaftLog independent validation handover — 2026-09-05

Validate the external RaftLog project on its own evidence. It is Quorus's sister
project at `C:\Users\mraysmit\dev\idea-projects\raftlog` (`../raftlog` from Quorus),
with its own repository and Maven reactor. Quorus consumes `raftlog-core`.

## Correction

Previous allegations of RaftLog 1.1.0 defects, a required 1.2.0 release, and validation
against commit `db59859` are **unsubstantiated**. They may originate in remnants of
the formerly integrated implementation; this origin has not been verified. Do not
assume they describe the current external library. Historical records are preserved
for traceability, with correction notices, rather than silently rewritten as proof.

## Confirmed by source inspection

- The inspected sister checkout declares 1.1.0; the previously observed revision is
  `7a3bd3a`. Recheck HEAD and the working tree in the validation session.
- Quorus's root POM requests 1.2.0. Resolution failed for that coordinate; the
  declaration does not demonstrate that a release exists.
- Quorus `RaftLogStorageAdapter.truncatePrefix` invokes an external method absent
  from the inspected 1.1.0 `RaftStorage` interface. Changing only the version cannot
  resolve this source incompatibility.
- The external interface documents `truncateSuffix` before appending conflicting
  replacements, followed by `sync` before acknowledgement. Its replay implementation
  removes entries at and above a TRUNCATE index and subsequently appends APPEND records.
  An overlapping raw append without the required truncation is not evidence of a
  library contract violation. These are source observations, not newly executed tests.
- Quorus owns a durable snapshot sidecar. Its storage interface promises prefix
  deletion to reclaim space. The absent external operation is a capability mismatch;
  do not silently emulate success, discard retained tails, or reintroduce an internal WAL.

Source locations: sister `raftlog-core/src/main/java/dev/mars/raftlog/storage/`
(`RaftStorage.java`, `FileRaftStorage.java`), its `FileRaftStorageTest`, and Quorus
`quorus-controller/src/main/java/dev/mars/quorus/controller/raft/` (`RaftNode.java`
and `storage/RaftLogStorageAdapter.java`).

## Work for the RaftLog validation session

1. Read project instructions, record HEAD, working changes and declared version, and
   run the existing suite against that actual source. Retain exact commands and logs.
2. Check documented append/truncate/sync/reopen behavior using real temporary storage,
   including conflicting replacements through the documented call sequence. Separate
   caller misuse, missing capabilities and reproduced library defects.
3. Assess the prefix-compaction capability Quorus needs, including durable snapshots,
   retained tails, restart and interrupted-operation safety. Report the existing
   contract and a concrete compatibility proposal; do not invent a release number.
4. If a defect is reproduced, follow strict TDD: retain an intended behavioral failing
   test before the fix, then focused green and relevant regression evidence. Compilation
   and dependency failures do not count as behavioral red. Mockito and replacement
   mocking frameworks are prohibited; use real implementations or purpose-built fakes.
5. Return the verified revision/version, test outcomes, independently substantiated
   findings and supported API to the Quorus session so its adapter and dependency can
   be reconciled and controller verification resumed.

R4, remaining R5 work, R6 full-reactor acceptance and R1 deployment durability gates
remain open in Quorus. The earlier 1,774-test core/workflow/agent verification does
not validate the external RaftLog library or close those gates. This correction changes
documentation only; no sister-project code, dependency, WAL or deployed data was changed.

## Returned independent validation — 2026-09-05

The requested validation is complete against RaftLog `7a3bd3acc26cab5a3fb85b3b266bd1e5c1d0f497`, POM `1.1.0`, with production sources unchanged. The source observations above are now supplemented by real-storage tests; the historical work list remains for traceability.

- Duplicate raw replay sequences were reproduced; correct append planning plus truncate–append–sync recovered the intended log. Conclusion: caller misuse/integration mismatch when raw append is expected to replace or deduplicate, not a substantiated RaftLog replay defect.
- Prefix compaction is a missing capability. A four-entry 128-byte WAL grew to 159 bytes after suffix truncation and recovered only the retained entries. Logical deletion is not physical reclamation. The former successful no-op is a Quorus adapter defect; application snapshots remain Quorus-owned.
- The claimed `db59859`/`1.2.0` fixes remain unsubstantiated by local objects/artifacts, advertised refs, and direct HTTP checks at 2026-09-05 12:30:57–12:31:01 +08:00. Central metadata reported latest/release 1.1.0 and the 1.2.0 POM returned HTTP 404. No compatible replacement dependency was published or selected.
- Sibling `FileRaftStorageRecoveryContractTest` has 22 passing cases, covering overlap/retry, suffix boundaries, term/payload preservation, CRC corruption, and 96 torn-tail fixtures followed by recovery and subsequent writes/restarts. `mvn.cmd -B clean verify` completed at 2026-09-05T12:48:46+08:00: 296 tests, zero failures/errors, three existing Windows skips; all three RaftLog reactor modules passed. JDK 25 compiled with release 21. These are characterization tests, not historical red/green evidence; they do not validate an absent compaction implementation or deployment power-loss durability.

The permanent Quorus requirements and next-dependency acceptance criteria are in [Appendix F.5](../design/QUORUS_RAFT_WAL_DESIGN.md#f5-independently-verified-contract-and-dependency-requirements--2026-09-05). Raw command output, timestamps, source hashes, and test patches remain in the sibling's Git-ignored `test-output/raftlog-validation-2026-09-05/` directory; no generated evidence files are added to Quorus. The retained test output uses the test's former name, `QuorusFindingsValidationTest`; it was subsequently renamed without behavioral changes.

This return updates documentation only. It does not close Quorus controller verification or deployment durability gates, modify dependency versions, or change deployed storage.

## Implemented capability and release — 2026-09-05

The preceding validation described RaftLog 1.1.0. Following explicit implementation authorization, RaftLog added prefix compaction under strict behavioral TDD and published a new `io.github.mraysmit:raftlog-core:1.2.0` release from commit **1c5af80f13a149663926c01eb15f88c14c4f2d25**, tag **v1.2.0**. This closes the missing external API/capability gap; it does not retroactively validate `db59859` or the earlier claimed artifact.

The implementation forces and atomically replaces the WAL, preserves retained entries and metadata, retains raw append semantics, and fences operations after uncertain publication. The [current integration contract](../design/QUORUS_RAFT_WAL_DESIGN.md#f5-independently-verified-contract-and-dependency-requirements--2026-09-05) defines snapshot ownership, failure handling, platform limits, costs and rollback constraints.

Evidence from RaftLog base `872a8c0`: nine initial behavioral failures, then 23 failing contract/fault cases (zero compilation errors), followed by all 23 passing. The tests use actual files and four abruptly terminated child JVMs. Windows `clean install` completed at 2026-09-05T13:59:37+08:00: 319 tests, zero failures/errors, three platform skips. Linux/JDK 21 `clean verify` completed at 2026-09-05T06:02:17Z: all 319 passed with no skips. These are process-interruption and directory-force checks, not machine power cuts.

Quorus verification completed at 2026-09-05T14:00:40+08:00 against the new locally installed artifact: **41 tests, zero failures/errors/skips**. Command: `mvn.cmd -B -f ../quorus/pom.xml -pl quorus-controller -am -Dtest=RaftStorageContractTest,RaftLogStorageAdapterTest,SnapshotRecoveryBoundaryTest,FollowerRestartConsistencyTest,ThreeControllerDurableRestartTest -Dsurefire.failIfNoSpecifiedTests=false test`. Dependent modules compiled; this selected run is not a full Quorus reactor acceptance run. The Quorus POM already requests 1.2.0 and needed no version change.

Central publication and direct artifact downloads were verified. The published core JAR SHA-256 is **7BB73A0F588FC8C5534296D4F0860F890A7D15789145DB742C2A4E3C8D75C426**; Central deployment **5d8e4410-c07d-4bb4-bb7e-b3846bee91cd**. Source: [release commit](https://github.com/mraysmit/raftlog/commit/1c5af80f13a149663926c01eb15f88c14c4f2d25). Artifact: [Central 1.2.0](https://repo.maven.apache.org/maven2/io/github/mraysmit/raftlog-core/1.2.0/).

Timestamped raw logs, source hashes, failure patches and artifact checks remain in the sibling's Git-ignored `test-output/prefix-compaction/`. No generated logs, application code changes or deployed-storage mutations are added to Quorus. Existing full-reactor and production-filesystem durability acceptance gates remain open.
