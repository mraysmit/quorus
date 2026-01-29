# Pluggable Storage Architecture - Implementation Plan

**Reference:** QUORUS_RAFT_WAL_DESIGN.md, Appendix F  
**Estimated Effort:** 3-4 days  
**Priority:** High (blocks WAL integration)

---

## Objective

Implement the `RaftStorage` SPI (Service Provider Interface) enabling drop-in replacement between the custom FileRaftStorage (default) and RocksDbRaftStorage (high-performance option).

---

## Deliverables

| # | Deliverable | Package | Status |
|---|-------------|---------|--------|
| 1 | `RaftStorage` interface | `raft.storage` | [ ] |
| 2 | `FileRaftStorage` implementation | `raft.storage.file` | [ ] |
| 3 | `RocksDbRaftStorage` implementation | `raft.storage.rocksdb` | [ ] |
| 4 | `InMemoryRaftStorage` (testing) | `raft.storage` | [ ] |
| 5 | `RaftStorageFactory` | `raft.storage` | [ ] |
| 6 | Configuration properties | `quorus-controller.properties` | [ ] |
| 7 | Unit tests | `raft.storage` (test) | [ ] |

---

## Implementation Steps

### Day 1: Interface & File Implementation

```
Morning:
├── Create package: dev.mars.quorus.controller.raft.storage
├── Define RaftStorage interface with full Javadoc
├── Define record types: PersistentMeta, LogEntryData
└── Create InMemoryRaftStorage for immediate testing

Afternoon:
├── Create package: dev.mars.quorus.controller.raft.storage.file
├── Implement FileRaftStorage
│   ├── open() - directory creation, FileChannel setup
│   ├── updateMetadata() - atomic temp→rename→dir-sync
│   ├── loadMetadata() - CRC validation
│   ├── appendEntries() - batch write records
│   ├── truncateSuffix() - write TRUNCATE record
│   ├── sync() - FileChannel.force(true)
│   └── replayLog() - sequential scan with CRC checks
└── Write unit tests for FileRaftStorage
```

### Day 2: RocksDB Implementation

```
Morning:
├── Add rocksdbjni dependency (optional scope)
├── Create package: dev.mars.quorus.controller.raft.storage.rocksdb
├── Implement RocksDbRaftStorage
│   ├── Key schema: meta:term, meta:votedFor, log:{index}
│   ├── WriteBatch for atomic operations
│   ├── Range delete for truncateSuffix()
│   └── Iterator scan for replayLog()
└── Handle native library loading

Afternoon:
├── Write unit tests for RocksDbRaftStorage
├── Create RaftStorageFactory
│   ├── Configuration-based instantiation
│   ├── Classpath validation for RocksDB
│   └── Error messages for missing dependencies
└── Add configuration properties to quorus-controller.properties
```

### Day 3: Integration & Testing

```
Morning:
├── Wire RaftStorageFactory into QuorusControllerVerticle
├── Update RaftNode to accept RaftStorage (interface only)
├── Verify existing tests pass with FileRaftStorage
└── Run same tests with RocksDbRaftStorage

Afternoon:
├── Write integration test: storage backend swap
├── Performance comparison: File vs RocksDB (100k entries)
├── Document any behavioral differences
└── Update QUORUS_RAFT_WAL_DESIGN.md if needed
```

### Day 4: Polish & Documentation

```
├── Code review fixes
├── Javadoc completion
├── README updates for configuration
└── Final test pass on Windows + Linux (Docker)
```

---

## Key Files to Create

```
quorus-controller/src/main/java/dev/mars/quorus/controller/raft/storage/
├── RaftStorage.java                    # Interface (SPI)
├── RaftStorageFactory.java             # Factory with config
├── InMemoryRaftStorage.java            # Test implementation
├── file/
│   └── FileRaftStorage.java            # Custom WAL
└── rocksdb/
    └── RocksDbRaftStorage.java         # RocksDB adapter

quorus-controller/src/test/java/dev/mars/quorus/controller/raft/storage/
├── RaftStorageContractTest.java        # Shared tests for all impls
├── FileRaftStorageTest.java            # File-specific tests
└── RocksDbRaftStorageTest.java         # RocksDB-specific tests
```

---

## Configuration

```properties
# quorus-controller.properties
quorus.raft.storage.type=file          # or "rocksdb"
quorus.raft.storage.path=/var/lib/quorus/data
quorus.raft.storage.fsync=true
```

---

## Acceptance Criteria

- [ ] `RaftNode` compiles with only `RaftStorage` interface (no impl imports)
- [ ] Switching `storage.type` requires zero code changes
- [ ] All implementations pass `RaftStorageContractTest`
- [ ] FileRaftStorage works without RocksDB on classpath
- [ ] RocksDbRaftStorage fails fast with clear error if JAR missing
- [ ] InMemoryRaftStorage supports `setFailOnSync()` for error testing

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| RocksDB native lib issues on Windows | Test with Docker; document Windows limitations |
| Performance regression with File impl | Establish baseline; batch writes aggressively |
| Interface too narrow for future needs | Design review before implementation |

---

## Dependencies

```xml
<!-- pom.xml - optional -->
<dependency>
    <groupId>org.rocksdb</groupId>
    <artifactId>rocksdbjni</artifactId>
    <version>9.0.0</version>
    <optional>true</optional>
</dependency>
```

---

## Sign-Off

| Role | Name | Date |
|------|------|------|
| Developer | | |
| Reviewer | | |
| QA | | |
