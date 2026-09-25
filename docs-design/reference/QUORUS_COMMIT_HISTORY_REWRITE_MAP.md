# Quorus Commit History Rewrite Map

**Version:** 1.0  
**Date:** 2026-09-25  
**Status:** Current

The repository history rewrite beginning at historical commit `6942fc5` changed commit identities while preserving the trees listed below. Live plans and evidence cite the reachable replacement IDs. This table preserves the old-to-new mapping for audit interpretation.

| Historical ID | Reachable replacement | Description |
|---|---|---|
| `b604505` | `dc447d4` | R6 storage-shutdown correction and accepted source tree |
| `0fefecb` | `8b3cf5c` | R4/R5 security handover remediation |
| `f8fb15e` | `a0103a0` | Serialized vote decisions and Docker test startup repair |
| `28f0530` | `1a8f2b3` | R2/R3 changes |
| `ffc3e64` | `db532fb` | Full-reactor verification with raftlog-core 1.2.0 |
| `038da9f` | `e7c9dbc` | Durable snapshots and recovery after compaction |
| `2d8ed83` | `7b07825` | External raftlog-only storage |
| `43cdd20` | `067bb45` | RaftLogStorageAdapter prefix truncation |

The pairs were verified as tree-identical during the 2026-09-24 documentation review. Consumers should resolve and cite the replacement column.
