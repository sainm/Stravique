# Phase 5 Controlled Multi-Item Workflow

## Purpose

Phase 5 removes two frictions left by the single-proposal workflow:

- A file changed by an applied vetted transformation can be used for the next proposal without a manual rescan.
- Multiple plan items can be previewed, applied, and restored as one confirmed batch.

The workflow remains local-first and safety-first. It does not add a second writer: every file edit still uses the existing backup, hash, atomic-write, and restore protections.

## Same-file re-proposal

A proposal normally requires the current file hash to equal the hash recorded by the scan. Phase 5 adds one exception:

- If the current hash differs from the scan hash, it is accepted only when it equals the `appliedSha256` of the latest `APPLIED` transformation run for the same project and relative path.

Consequences:

- A second item on the same file can be proposed after an earlier vetted transform; its proposal records the current post-transform hash.
- Any manual edit after the apply produces a hash that matches neither the scan nor the latest applied run, so it remains `SOURCE_CHANGED`.
- A restored run is not treated as current content; only the latest `APPLIED` run is considered.
- The plan itself still comes from the scan. Re-proposal reuses the plan's item and evidence; a rescan is still required to refresh inventory evidence or a stale plan.

## Batch API

All batch operations are project-scoped and require a current plan for preview/apply.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/projects/{id}/migration-plans/{planId}/transformation-batches/preview` | Generate proposals for up to 50 items |
| `POST` | `/projects/{id}/migration-plans/{planId}/transformation-batches/apply` | Re-propose and apply up to 50 items with `{ "confirmed": true }` |
| `POST` | `/projects/{id}/migration-plans/{planId}/transformation-batches/restore` | Restore up to 50 transformation runs from the plan with `{ "confirmed": true }` |

Preview never writes a source file. It persists one proposal per item, superseding any earlier proposal for that item, and returns per-item proposals or localized per-item errors. Same-file previews all use the current source; apply is authoritative because it re-proposes each item in order.

Apply:

- Re-proposes each selected item against the current source immediately before applying it.
- Processes items in request order, so same-file items see earlier edits through the same-file rule.
- Applies each item with its normal confirmation, backup, hash, and atomic-write guards.
- On a per-item failure, stops and restores the items already applied in that batch in reverse order.
- Returns `APPLIED`, `FAILED`, or `ROLLBACK_FAILED`, with one entry per requested item: `APPLIED`, `FAILED`, `ROLLED_BACK`, `ROLLBACK_FAILED`, or `NOT_ATTEMPTED` for the items the fail-fast stop never reached.
- A failed batch is a best-effort compensating operation, not a distributed transaction across files. A rollback failure is reported explicitly and a run remains applied.

Restore:

- Restores each run independently in request order.
- Returns `RESTORED`, `PARTIAL`, or `FAILED`; no compensating rollback is attempted for a partial restore.
- Each restored run is still subject to the existing applied-hash check.

Every batch preview/apply/restore records an audit event with the item/run count and outcome. Each individual proposal/run remains independently auditable and retryable from the item inspector.

## UI

The plan list has a batch checkbox per included item. Selecting at least one item reveals a batch bar:

- Preview selected: opens the per-item proposal result.
- Apply selected: requires a second explicit confirmation checkbox; the confirmation explains sequential re-proposal and compensating restore.
- Clear selection.

The batch result modal shows the batch status, counts, per-item state, localized error text, and a restore action for runs that remain applied. The action is disabled until its own confirmation checkbox is selected.

## Limits and boundaries

- At most 50 items/runs per request; the request is rejected above that limit.
- Excluded items and unsupported rules produce per-item errors; they do not fabricate a proposal.
- Preview results for later same-file items can become stale once an earlier item is applied; apply intentionally re-proposes them.
- No compile/runtime validation, external transaction manager, or remote repository write is added.
- The existing individual inspector remains the place for a detailed diff and single-item restore.

## Verification

Integration tests cover:

- Same-file re-proposal and apply without a rescan.
- External modification after an apply is rejected.
- Batch preview, apply, and restore on a copied fixture.
- Failure rollback: one item applies, the next fails, and the first run is restored.
- Byte-exact fixture restoration.

Frontend and browser checks cover the batch selection bar, preview result, confirmation modal, and three-language layouts.
