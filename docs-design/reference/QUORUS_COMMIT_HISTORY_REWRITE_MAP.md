<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Quorus Commit History Rewrite Map

**Version:** 1.1  
**Date:** 2026-10-03  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Current

A history rewrite in September 2026 changed commit identities while preserving the trees listed below. Live plans cite the replacement IDs. This table preserves the old-to-new mapping for audit interpretation.

| Original ID | Replacement (cite this) | Original reachable from `master` on 2026-10-03 | Description |
|---|---|---|---|
| `b604505` | `dc447d4` | No: on no ref | R6 storage-shutdown correction and accepted source tree |
| `0fefecb` | `8b3cf5c` | No: on no ref | R4/R5 security handover remediation |
| `f8fb15e` | `a0103a0` | No: on no ref | Serialized vote decisions and Docker test startup repair |
| `28f0530` | `1a8f2b3` | Yes | R2/R3 changes |
| `ffc3e64` | `db532fb` | Yes | Full-reactor verification with raftlog-core 1.2.0 |
| `038da9f` | `e7c9dbc` | Yes | Durable snapshots and recovery after compaction |
| `2d8ed83` | `7b07825` | Yes | External raftlog-only storage |
| `43cdd20` | `067bb45` | Yes | RaftLogStorageAdapter prefix truncation |

Every pair was re-verified as tree-identical on 2026-10-03, and every replacement is an ancestor of `master`.

The original line of history is not entirely gone. The merge `e302e41` (2026-10-02) brought the pre-rewrite commits from `origin/master` back into `master`, so five of the originals are reachable again alongside their replacements. The first three are on no branch or tag, and a fresh clone cannot resolve them. Because both lines now coexist, always cite the replacement column; an original ID found in an older record means the same tree as its replacement.
