<img src="../../docs/quorus-logo.png" alt="Quorus" width="120"/>

# ADR-0010: Workflow YAML Semantics

**Version:** 1.0  
**Date:** 2026-10-20  
**Author:** Mark Ray-Smith — Cityline Ltd  
**License:** Apache 2.0

**Status:** Accepted for decisions `DR-Q1` and `ENG-Q1`, taken by project authority on 2026-10-03, in the [Outstanding Work Register](../task/QUORUS_OUTSTANDING_WORK_REGISTER_2026-10-20.md#3-decision-log). Items 6 to 9 were chosen while delivering `ENG-14` on 2026-10-18; they are marked **to confirm** and await the owner's confirmation.

**Relates to:** the [YAML Syntax Guide](../../docs/QUORUS_YAML_SYNTAX_GUIDE.md), which is the user reference for these rules. This ADR records why the rules are what they are.

## Context

The 2026-09-24 documentation review found that the workflow YAML accepted settings and expressions that did nothing:

- Transfer `options` were parsed, variable-resolved and then dropped. The example workflows used 21 option keys, such as `timeout`, `chunkSize` and `maxRetries`, and none had any effect.
- A variable reference inside another variable's value stayed literal text. The complex example therefore wrote to a directory named `{{outputDir}}`.
- Group and transfer `condition` expressions were resolved and then ignored, so a guarded transfer always ran.
- `execution.dryRun`, `execution.virtualRun`, `execution.timeout`, `execution.parallelism` and group `retryCount` were ignored, and `strategy` accepted any value.

The execution settings were fixed by `RT-04` and `ENG-12` (timeout, parallelism) and by `ENG-13` (dry run, virtual run, retry count, strategy). Decision `DR-Q1` settled options and nesting, and `ENG-Q1` settled conditions. The principle behind all three is the same: **the YAML must not accept a setting that nothing acts on.**

## Decision

### Transfer options (`DR-Q1`, delivered by `ENG-26`)

1. **A transfer accepts two options**, each mapped to a field of the transfer request that the engine or every protocol adapter acts on:
   - `checksum`: the expected SHA-256 of the file, 64 hexadecimal digits. Every adapter verifies it and fails the transfer on a mismatch.
   - `expectedSize`: the file size in whole bytes. The engine reports it as the transfer's total for progress.

   No adapter reads the request's free-form metadata, so passing other keys through would only move the silent drop elsewhere. A new option is added only together with the code that acts on it.

2. **Any other key fails engine validation**, with a field path such as `spec.transferGroups[0].transfers[1].options.timeout`, and no transfer of that workflow starts. A literal value is checked at validation. A value holding a variable reference is checked when the transfer request is built, by the same rules, and an invalid one fails its transfer before the engine is called.

### Variables (`DR-Q1`)

3. **A reference inside a variable's value is resolved**, recursively, to a depth of 10. A cycle or deeper nesting fails resolution with the chain in the message, for example `a -> b -> a`. Precedence, highest first: group variables, workflow `spec.variables`, the runtime variables of the execution context, environment variables, system properties. A caller therefore cannot override a value the YAML declares.

### Conditions (`ENG-Q1`, delivered by `ENG-14`)

4. **The grammar** is `success(name)`, `failure(name)`, `file_exists('path')`, `not`, `and`, `or` and parentheses. `not` binds tightest, then `and`, then `or`. Keywords are lower case. Anything outside the grammar fails engine validation.

5. **Variable references are allowed only inside a `file_exists` path**, so that every name a condition uses is known at validation.

6. **To confirm. A condition may name only what has finished**: a group this group depends on, directly or transitively, or a transfer of such a group. A transfer of the same group runs in parallel, so it is refused rather than given hidden ordering. A name must be exactly one group or one transfer. The stricter rule was chosen because relaxing it later breaks no workflow, while tightening it would.

7. **To confirm. Outcomes:** `success(x)` is true when `x` ran and succeeded, and `failure(x)` when it ran and failed. Both are false when `x` was skipped. `failure(x)` on a group without `continueOnError` is a validation warning, because the workflow stops when that group fails.

8. **To confirm. A false condition skips.** A skipped group has status `SKIPPED` and runs no transfer. A skipped transfer has no result and is listed in its group's skipped transfers. A skip is not a failure: the workflow can complete, and a skipped group's dependents still run, subject to their own conditions.

9. **To confirm. Evaluation:** a group's condition is evaluated when its dependencies have finished, and a transfer's when its group starts. `file_exists` checks the filesystem of the machine running the workflow engine, after variable resolution. A condition that cannot be evaluated at run time fails its group or transfer without starting it. Normal and virtual runs evaluate conditions; a dry run does not.

### Execution settings (delivered by `RT-04`, `ENG-12` and `ENG-13`)

10. `execution.timeout` (default one hour) bounds the whole run; at the deadline the running transfers are interrupted and the execution fails. `execution.parallelism` (default one) is the most groups that run at once, in dependency order. A group's `retryCount` re-runs each of its failed transfers up to that many times. `execution.dryRun` and `execution.virtualRun` can only make a run safer: either flag means no real transfer starts, and a dry run wins. `strategy` must be `sequential` or `parallel` and does not change scheduling; `parallelism` does.

## Alternatives considered

- **Pass every option through as request metadata.** Rejected: no adapter reads metadata, so the options would still have no effect, only less visibly.
- **Accept and warn on unknown options.** Rejected by `DR-Q1`: a warning is easy to miss, and a workflow that declares `timeout: 30s` would still run without one.
- **Leave nested references literal, or fail on any nesting.** Rejected by `DR-Q1`: nested variables are common in the examples, and leaving them literal produced wrong paths silently.
- **Reject workflows that declare conditions.** The other option in `ENG-14`. Rejected by `ENG-Q1` in favour of a small, closed grammar.
- **Let a condition name a sibling transfer and wait for it.** Not chosen (item 6): it would add ordering inside a group that the YAML does not show. A workflow that needs it uses a later group that depends on the first.
- **A general expression language** such as SpEL, which the module already depends on. Not chosen: a closed grammar can be fully validated before a run, and an expression language cannot.

## Consequences

- Workflows that used other option keys, or conditions that named a sibling transfer, now fail validation. The two test fixtures and all seven example workflows were changed with the deliveries, and the e-commerce and financial examples gained dependent groups.
- A variable value containing `{{` is now resolved where it was previously literal, and a reference to an undefined variable inside a value now fails the run. The financial example states which variables its execution context must supply.
- A dry run reports every transfer as succeeded without building requests, so it does not check templated option values or evaluate conditions. Making it build requests would close the first gap without network access; that is a separate change.
- Adding an option means adding its handling to `TransferOptions`, its acting code, its tests and the YAML Syntax Guide together.

## Revision history

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-10-20 | Records decisions `DR-Q1` and `ENG-Q1` as delivered by `ENG-26` and `ENG-14`, and the execution settings delivered by `RT-04`, `ENG-12` and `ENG-13`. Items 6 to 9 await confirmation |
