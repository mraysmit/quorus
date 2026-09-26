<img src="../../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# Raw Evidence Index

**Version:** 1.0  
**Date:** 2026-09-26  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0  
**Status:** Active — raw command output retained as evidence (decision `DR-Q6`)  
**Scope:** Raw logs cited by the evidence manifests and the enterprise plan

## Rule

Raw red, green, regression and verification output that a plan, register or evidence record cites is written directly to `docs-design/evidence/raw/<slice-id>/`. It is never written to `temp/`, which is git-ignored scratch space and may be deleted at any time. Each file is recorded with its SHA-256 in the slice's JSON manifest. Logs must not contain request bodies, credentials, keys or other sensitive payloads (plan §6.1).

## Rescued historical logs

Before this rule existed, evidence was written to `temp/`. On 2026-09-26 every surviving cited file was copied here unchanged, keeping its path below `temp/`. The manifests and plan still cite the original `temp/` paths because they are historical records. Use this table to resolve them. "Matches manifest" means the copy's SHA-256 equals the hash recorded when the evidence was captured.

Cited paths: 220. Rescued: 62 (47 matches manifest, 15 no recorded hash). Missing: 153. Not retained (build files): 5.

| Original path | Rescued copy | Bytes | SHA-256 | Check | Cited by |
|---|---|---:|---|---|---|
| `temp/docker-fixture-green-20260905.log` | [docker-fixture-green-20260905.log](docker-fixture-green-20260905.log) | 61,235 | `1beaa79a650d74f8…` | matches manifest | full-suite-error-remediation-2026-09-05.json, full-suite-error-remediation-2026-09-05.md |
| `temp/docker-fixture-rebuild-20260905.log` | [docker-fixture-rebuild-20260905.log](docker-fixture-rebuild-20260905.log) | 38,437 | `1fb7f9723ed580ea…` | no recorded hash | full-suite-error-remediation-2026-09-05.md |
| `temp/full-suite-20260905.log` | [full-suite-20260905.log](full-suite-20260905.log) | 6,599,126 | `3178d907496d40d3…` | matches manifest | full-suite-error-remediation-2026-09-05.json, full-suite-error-remediation-2026-09-05.md |
| `temp/full-suite-docker-controller2-20260905.log` | [full-suite-docker-controller2-20260905.log](full-suite-docker-controller2-20260905.log) | 10,415 | `66b714a0ee24b03f…` | no recorded hash | full-suite-error-remediation-2026-09-05.md |
| `temp/full-suite-fixed-20260905.log` | [full-suite-fixed-20260905.log](full-suite-fixed-20260905.log) | 7,088,579 | `117b552c5310dc56…` | matches manifest | full-suite-error-remediation-2026-09-05.json, full-suite-error-remediation-2026-09-05.md |
| `temp/r4-dns-capacity-green.log` | [r4-dns-capacity-green.log](r4-dns-capacity-green.log) | 66,979 | `6bc5f2ff811f3aee…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-capacity-red.log` | [r4-dns-capacity-red.log](r4-dns-capacity-red.log) | 101,477 | `da50e91c214f22b7…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-docker-rebuild.log` | [r4-dns-docker-rebuild.log](r4-dns-docker-rebuild.log) | 111,065 | `e1a8f0a514cb506d…` | no recorded hash | r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-eventloop-green.log` | [r4-dns-eventloop-green.log](r4-dns-eventloop-green.log) | 25,803 | `f855b1476e79f2d8…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-eventloop-red.log` | [r4-dns-eventloop-red.log](r4-dns-eventloop-red.log) | 51,675 | `117c29f4af433018…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-expiry-red.log` | [r4-dns-expiry-red.log](r4-dns-expiry-red.log) | 28,437 | `c913a26cc94f1171…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-final-docker-rebuild.log` | [r4-dns-final-docker-rebuild.log](r4-dns-final-docker-rebuild.log) | 107,778 | `0330dc0758714a00…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-final-focused.log` | [r4-dns-final-focused.log](r4-dns-final-focused.log) | 312,549 | `794c4fe2679ddf28…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-focused-regression.log` | [r4-dns-focused-regression.log](r4-dns-focused-regression.log) | 306,622 | `4ddcf5efc480be14…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-full-verify.log` | [r4-dns-full-verify.log](r4-dns-full-verify.log) | 7,197,089 | `45057eea0499deff…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-revocation-green.log` | [r4-dns-revocation-green.log](r4-dns-revocation-green.log) | 101,483 | `e7165ce08afe8bf1…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r4-dns-revocation-red.log` | [r4-dns-revocation-red.log](r4-dns-revocation-red.log) | 118,583 | `6ef59b222874571b…` | matches manifest | r4-dns-remediation-2026-09-05.json, r4-dns-remediation-2026-09-05.md |
| `temp/r5-closure-codec-green.log` | [r5-closure-codec-green.log](r5-closure-codec-green.log) | 75,229 | `bbfe5e87136e03b6…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-codec-red.log` | [r5-closure-codec-red.log](r5-closure-codec-red.log) | 10,581 | `a18efc7bcbd7c27a…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-events-behavioral-red.log` | [r5-closure-events-behavioral-red.log](r5-closure-events-behavioral-red.log) | 32,281 | `bd5ffc82eaf1addf…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-events-green.log` | [r5-closure-events-green.log](r5-closure-events-green.log) | 35,826 | `e299aaeca061e85f…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-events-red.log` | [r5-closure-events-red.log](r5-closure-events-red.log) | 30,372 | `b0435d432de2fc48…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-focused-regression.log` | [r5-closure-focused-regression.log](r5-closure-focused-regression.log) | 449,796 | `027da7dbc90b68b4…` | matches manifest | r5-closure-2026-09-05.json |
| `temp/r5-closure-legacy-replay-green-2.log` | [r5-closure-legacy-replay-green-2.log](r5-closure-legacy-replay-green-2.log) | 27,045 | `7146a1d102df8eb4…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-legacy-replay-green.log` | [r5-closure-legacy-replay-green.log](r5-closure-legacy-replay-green.log) | 68,499 | `ae1cf23dfd3e7c9b…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-legacy-replay-red.log` | [r5-closure-legacy-replay-red.log](r5-closure-legacy-replay-red.log) | 19,054 | `16c10d8baa13b36f…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-route-trust-green.log` | [r5-closure-route-trust-green.log](r5-closure-route-trust-green.log) | 38,029 | `8507b5cf7006b7b5…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-route-trust-red.log` | [r5-closure-route-trust-red.log](r5-closure-route-trust-red.log) | 42,000 | `4e6307a72d1c7a3c…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-trust-cache-green.log` | [r5-closure-trust-cache-green.log](r5-closure-trust-cache-green.log) | 43,233 | `3793fa7fe7bb2408…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r5-closure-trust-cache-red.log` | [r5-closure-trust-cache-red.log](r5-closure-trust-cache-red.log) | 12,192 | `e629973590e071c3…` | no recorded hash | r5-closure-2026-09-05.json |
| `temp/r6-final-worktree-2/temp/r6-final-isolated-docker-build.log` | [r6-final-worktree-2/temp/r6-final-isolated-docker-build.log](r6-final-worktree-2/temp/r6-final-isolated-docker-build.log) | 38,594 | `9a0d0ebdf32e81a4…` | matches manifest | r6-final-acceptance-2026-09-05.json |
| `temp/r6-final-worktree-2/temp/r6-final-isolated-full.log` | [r6-final-worktree-2/temp/r6-final-isolated-full.log](r6-final-worktree-2/temp/r6-final-isolated-full.log) | 7,205,659 | `f6010c257aac98c1…` | matches manifest | r6-final-acceptance-2026-09-05.json |
| `temp/r6-final-worktree/temp/r6-isolated-full.log` | [r6-final-worktree/temp/r6-isolated-full.log](r6-final-worktree/temp/r6-isolated-full.log) | 7,165,493 | `bdee7391f8df3302…` | matches manifest | r6-final-acceptance-2026-09-05.json, r6-final-acceptance-2026-09-05.md |
| `temp/r6-isolation-fix-slow-green.log` | [r6-isolation-fix-slow-green.log](r6-isolation-fix-slow-green.log) | 201,262 | `9391a3354e9df386…` | matches manifest | r6-final-acceptance-2026-09-05.json |
| `temp/remediation-20260905/isolated-core-agent-verify.log` | [remediation-20260905/isolated-core-agent-verify.log](remediation-20260905/isolated-core-agent-verify.log) | 1,841,159 | `4241c07ea824b442…` | matches manifest | remediation-r4-r6-2026-09-05.json, remediation-r4-r6-2026-09-05.md |
| `temp/remediation-20260905/r4-1-red-elevated.log` | [remediation-20260905/r4-1-red-elevated.log](remediation-20260905/r4-1-red-elevated.log) | 12,781 | `ad3cfd9e72d1c017…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r4-1-red-online.log` | [remediation-20260905/r4-1-red-online.log](remediation-20260905/r4-1-red-online.log) | 11,608 | `271a5a2c80057f97…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r4-1-red.log` | [remediation-20260905/r4-1-red.log](remediation-20260905/r4-1-red.log) | 25,806 | `6f7ed70e49ce632a…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-1-core-verify.log` | [remediation-20260905/r5-1-core-verify.log](remediation-20260905/r5-1-core-verify.log) | 1,247,975 | `f3a3ddfa609750b7…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-1-green.log` | [remediation-20260905/r5-1-green.log](remediation-20260905/r5-1-green.log) | 3,354 | `8949cc84a0f7eaf4…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-1-red-behavior.log` | [remediation-20260905/r5-1-red-behavior.log](remediation-20260905/r5-1-red-behavior.log) | 86,978 | `a315724fc71a1a90…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-1-red-scopes.log` | [remediation-20260905/r5-1-red-scopes.log](remediation-20260905/r5-1-red-scopes.log) | 161,557 | `c7f24197b94f0245…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-1-red.log` | [remediation-20260905/r5-1-red.log](remediation-20260905/r5-1-red.log) | 6,093 | `89da2a645da3ad25…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-2-entrypoint-green.log` | [remediation-20260905/r5-2-entrypoint-green.log](remediation-20260905/r5-2-entrypoint-green.log) | 1,374 | `aa753b31b9d41a51…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-2-entrypoint-precedence-red.log` | [remediation-20260905/r5-2-entrypoint-precedence-red.log](remediation-20260905/r5-2-entrypoint-precedence-red.log) | 262 | `4fefe9d544e147ca…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-2-entrypoint-red.log` | [remediation-20260905/r5-2-entrypoint-red.log](remediation-20260905/r5-2-entrypoint-red.log) | 230 | `3fbcd7718ed58507…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-3-4-red.log` | [remediation-20260905/r5-3-4-red.log](remediation-20260905/r5-3-4-red.log) | 76,202 | `9b2184e6b25999c5…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-3-green.log` | [remediation-20260905/r5-3-green.log](remediation-20260905/r5-3-green.log) | 45,329 | `79273b92d3608dac…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-4-agent-red.log` | [remediation-20260905/r5-4-agent-red.log](remediation-20260905/r5-4-agent-red.log) | 34,727 | `26e7700d425fb159…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-4-green-implemented.log` | [remediation-20260905/r5-4-green-implemented.log](remediation-20260905/r5-4-green-implemented.log) | 445,250 | `d086ae02c9578ee2…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-4-green.log` | [remediation-20260905/r5-4-green.log](remediation-20260905/r5-4-green.log) | 408,701 | `6310e218fdf2644c…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-4-security-red.log` | [remediation-20260905/r5-4-security-red.log](remediation-20260905/r5-4-security-red.log) | 45,747 | `30de012a467716be…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-5-green-worker.log` | [remediation-20260905/r5-5-green-worker.log](remediation-20260905/r5-5-green-worker.log) | 24,768 | `d2c1683370355ca1…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-5-green.log` | [remediation-20260905/r5-5-green.log](remediation-20260905/r5-5-green.log) | 17,947 | `02655f09a79937a5…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-5-red.log` | [remediation-20260905/r5-5-red.log](remediation-20260905/r5-5-red.log) | 12,791 | `9f835a25f2d0eae9…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-5-worker-red.log` | [remediation-20260905/r5-5-worker-red.log](remediation-20260905/r5-5-worker-red.log) | 12,956 | `f00f561e3a53f646…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-6-worker-green.log` | [remediation-20260905/r5-6-worker-green.log](remediation-20260905/r5-6-worker-green.log) | 85,028 | `8b8b9ea7e566b53a…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r5-6-worker-red.log` | [remediation-20260905/r5-6-worker-red.log](remediation-20260905/r5-6-worker-red.log) | 32,190 | `9373a264e2c337d4…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r6-controller-online-refresh.log` | [remediation-20260905/r6-controller-online-refresh.log](remediation-20260905/r6-controller-online-refresh.log) | 2,216 | `97eed457c34edadf…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/r6-controller-preflight.log` | [remediation-20260905/r6-controller-preflight.log](remediation-20260905/r6-controller-preflight.log) | 2,026 | `d777c5bcaefc3104…` | matches manifest | remediation-r4-r6-2026-09-05.json |
| `temp/vote-boundary-green-20260905.log` | [vote-boundary-green-20260905.log](vote-boundary-green-20260905.log) | 204,568 | `75db4d4132a84c21…` | matches manifest | full-suite-error-remediation-2026-09-05.json, full-suite-error-remediation-2026-09-05.md |
| `temp/vote-boundary-red-20260905.log` | [vote-boundary-red-20260905.log](vote-boundary-red-20260905.log) | 78,176 | `46ce1d940988ee80…` | matches manifest | full-suite-error-remediation-2026-09-05.json, full-suite-error-remediation-2026-09-05.md |

## Missing logs

These cited files no longer exist anywhere in the working copy. The statements that cite them remain as historical records, but their raw output cannot be re-inspected.

| Original path | Cited by |
|---|---|
| `temp/R3LinuxPathTests.java` | phase4-tdd-evidence-2026-09-03.json |
| `temp/jacoco-controller-clean-verify-authoritative.txt` | tdd-remediation-2026-09-01.json |
| `temp/jacoco-controller-clean-verify-retry.txt` | tdd-remediation-2026-09-01.json |
| `temp/jacoco-controller-clean-verify.txt` | tdd-remediation-2026-09-01.json |
| `temp/phase1-audit-evidence-red.txt` | tdd-remediation-2026-09-01.json |
| `temp/phase1-audit-integrity-green.txt` | tdd-remediation-2026-09-01.json |
| `temp/phase1-audit-integrity-red.txt` | tdd-remediation-2026-09-01.json |
| `temp/phase1-completion-green-1.txt` | tdd-remediation-2026-09-01.json |
| `temp/phase1-completion-red.txt` | tdd-remediation-2026-09-01.json |
| `temp/phase1-final-clean-verify.txt` | tdd-remediation-2026-09-01.json |
| `temp/phase2-agent-clean-verify-final.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-agent-client-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-agent-client-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-agent-protocol-controller-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-agent-protocol-green-final.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-agent-protocol-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-assignment-agent-flow-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-atomic-assignment-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-atomic-assignment-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-attempt-assignment-green-final.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-attempt-fencing-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-attempt-http-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-clean-verify-1.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-controller-clean-verify-final.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-expected-state-http-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-expired-lease-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-foundation-external-green-final.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-lifecycle-atomicity-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-lifecycle-atomicity-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-lifecycle-atomicity-regression.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-rest-atomic-assignment-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-rest-atomic-assignment-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-stale-fence-http-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-terminal-http-retry-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-terminal-http-retry-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-terminal-outcome-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-terminal-retry-red.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-terminal-retry-regression-green.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase2-transfer-attempt-model-green-final.txt` | phase2-tdd-evidence-2026-09-02.json |
| `temp/phase3-` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md |
| `temp/phase3-active-stall-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-active-stall-red.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-controller-clean-verify-events-retry.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md |
| `temp/phase3-controller-clean-verify-events.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-controller-clean-verify-final.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md |
| `temp/phase3-controller-clean-verify-stall.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-controller-clean-verify.txt` | phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-event-ledger-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-event-ledger-red.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-event-offer-red-final.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-event-offer-restore-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-event-offer-restore-red.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-event-vocabulary-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-event-vocabulary-red.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-lifecycle-events-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-lifecycle-events-red.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-missing-telemetry-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-missing-telemetry-red-final.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-progress-http-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-progress-http-red-final.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-progress-policy-green.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-progress-policy-red.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase3-progress-regression.txt` | QUORUS_ENTERPRISE_IMPLEMENTATION_PLAN.md, phase3-tdd-evidence-2026-09-02.json |
| `temp/phase4-agent-runtime-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-agent-runtime-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-approved-ca-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-auth-mode-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-auth-tls-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-credential-uri-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-credential-uri-http-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-credential-uri-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-final-focused-regression.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-governed-upload-final-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-governed-upload-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-lifecycle-events-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-lifecycle-protocol-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-mounted-filesystem-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-openapi-authz-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-openapi-authz-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-openapi-final-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-policy-secret-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-agent-placement-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-agent-placement-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-boundaries-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-boundaries-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-ca-anchor-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-ca-anchor-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-connection-use-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-lifecycle-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-secret-expiry-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-socket-binding-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-socket-binding-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-validation-probe-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-remediation-validation-probe-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-service-connection-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-service-connection-http-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-tls-floor-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-tls-peer-pin-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-vault-validation-green.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/phase4-vault-validation-red.txt` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-controller-clean-verify.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-final-controller-verify.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-http-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-initial-green.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-migration-boundary-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-regression-corrected-green.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-regression-green.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-schema-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r2-state-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-agent-contracts-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-boundaries-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-clean-verify.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-focused-green.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-linux-path-policy-green.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-linux-path-policy.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-pre-execution-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-preparation-dedup-green.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r3-preparation-dedup-red.log` | phase4-tdd-evidence-2026-09-03.json |
| `temp/r6-final-isolated-full.log` | r6-final-acceptance-2026-09-05.json, r6-final-acceptance-2026-09-05.md |
| `temp/r6-final-worktree-2` | r6-final-acceptance-2026-09-05.json, r6-final-acceptance-2026-09-05.md |
| `temp/raft-log-adopt-120-clean-verify.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-adopt-120-green.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-adopt-120-regress.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-consistency-green.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-consistency-red.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-consistency-regress.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-library-install.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-library-prefix-red.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-log-library-replay-red.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-storage-removal-green.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raft-storage-removal-red.txt` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raftlog-only-controller-verify.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raftlog-only-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/raftlog-only-red.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-20260905` | remediation-r4-r6-2026-09-05.md |
| `temp/remediation-20260905/verify-worktree` | remediation-r4-r6-2026-09-05.md |
| `temp/remediation-r1-boundary-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-boundary-red.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-clean-verify.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-final-controller-verify.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-install-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-install-red.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-interruption-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-interruption-red.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-ordering-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-ordering-red.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-red.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-repeat-recovery-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-repeat-recovery-red.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-slow-cluster-enabled.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-suffix-green.log` | raft-log-tdd-evidence-2026-09-04.json |
| `temp/remediation-r1-suffix-red.log` | raft-log-tdd-evidence-2026-09-04.json |

## Build inputs and outputs not retained

These cited paths exist but are not logs: a temporary worktree `pom.xml` used as a `-f` argument, and JAR files built from it. They are reproducible from the cited revision, and the manifests record artifact digests where they matter, so they are not kept here.

| Original path | Bytes | Cited by |
|---|---:|---|
| `temp/remediation-20260905/verify-worktree/pom.xml` | 7,222 | remediation-r4-r6-2026-09-05.json, remediation-r4-r6-2026-09-05.md |
| `temp/remediation-20260905/verify-worktree/quorus-agent/target/quorus-agent-1.0-SNAPSHOT.jar` | 79,763 | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/verify-worktree/quorus-core/target/quorus-core-1.0-SNAPSHOT-tests.jar` | 732,560 | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/verify-worktree/quorus-core/target/quorus-core-1.0-SNAPSHOT.jar` | 291,680 | remediation-r4-r6-2026-09-05.json |
| `temp/remediation-20260905/verify-worktree/quorus-workflow/target/quorus-workflow-1.0-SNAPSHOT.jar` | 68,357 | remediation-r4-r6-2026-09-05.json |
