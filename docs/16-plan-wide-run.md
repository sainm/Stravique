# Phase 7 Plan-Wide Controlled Run

## Purpose

Phase 7 adds a plan-level preflight and a controlled run over every executable item in one migration plan:

- Preflight is read-only. It reports which items are executable, which have no rule, which are not ready, which are excluded, and why a rule-backed item still cannot produce a proposal.
- A run requires explicit confirmation and applies eligible items in dependency phase order.
- Any failure restores every run applied by that plan run and reports the failure. A rollback failure is explicit.
- The run, its per-item outcomes, and its restore are persisted and audited.

The legacy transformation APIs remain item- or selected-batch-scoped. A plan run is a separate record; it does not change any migration item's planning status.

## Eligibility

An item is executable only when all of the following hold:

1. It is included in the plan.
2. Its planning status is `READY`.
3. A vetted rule matches its item type, target, and file-name slot.
4. A read-only probe can build a proposal from the current source and current target.

The probe runs the same prepare path as a normal proposal: it validates plan currency, source and target hashes, companion files, rule output, syntax, and create-target availability. It persists no proposal and writes no file. This is why an item such as a JSP page with a vetted JSP rule but no eligible tag is not executable: the rule exists, but the current file has nothing to transform.

`ineligibleReason` values are `EXCLUDED`, `NOT_READY`, `NO_RULE`, or the API error code returned by the probe, such as `TRANSFORMATION_UNSUPPORTED` or `SOURCE_CHANGED`.

## Coverage response

`GET /api/v1/projects/{projectId}/migration-plans/{planId}/transformation-coverage`

The response contains the plan and scan identifiers, the vetted rule count, a summary, and one row per plan item:

| Summary field | Meaning |
| --- | --- |
| `items` | All items in the plan |
| `supported` | Items with a matching vetted rule |
| `unsupported` | Items without a matching vetted rule |
| `eligible` | Items that pass every eligibility condition |
| `excluded` | Items outside the current plan scope |
| `reviewRequired` / `blocked` | Planning status counts |
| `proposals` | Persisted proposals for the plan |
| `appliedRuns` / `restoredRuns` | Persisted run counts |

Each row also carries its rule ID and version when matched, proposal and run counts, latest proposal and run statuses, and the `ineligibleReason` when it is not executable. The Migration view renders the rows, defaults to the executable subset, and lets the user select all or individual eligible items for a targeted run; unsupported and non-ready rows show their reason and cannot be selected.

## Plan run

`POST /api/v1/projects/{projectId}/migration-plans/{planId}/transformation-plan-runs`

The request requires `{ "confirmed": true }`, accepts an optional `itemIds` subset, and returns `202 Accepted` with a persisted `RUNNING` plan run; execution continues in the background. The run:

1. Recomputes coverage under the project lock and refuses a second `RUNNING` run for the same plan. An external source change between preview and run therefore removes the item from the executable set instead of applying a stale proposal.
2. Selects the requested subset, or every eligible item when `itemIds` is absent or empty. Every requested identifier must be currently eligible; request order is ignored and plan dependency order is preserved.
3. Persists the `RUNNING` header and a child row for every selected item, then submits the worker.
4. Processes selected items in internal chunks of 50, preserving one plan-run record and one rollback scope across all chunks.
5. For each item, re-proposes against the current source and applies the proposal with the normal confirmation and hash guards, checking the cancel flag before each item.
6. On success, records `APPLIED`, the selected and applied counts, and an audit event.
7. On failure, marks the failing child `FAILED`, restores every run already applied by this plan run in reverse order across earlier chunks, marks children `ROLLED_BACK` or `ROLLBACK_FAILED`, and records the plan run as `FAILED` or `ROLLBACK_FAILED`.
8. On a confirmed cancel request, stops before the next item, restores applied children in reverse order, marks remaining children `NOT_ATTEMPTED`, and records `CANCELLED` or `ROLLBACK_FAILED`.
9. Items after a failure or cancel are never written. Cancel, resume, and recover operate only on the selected children of the run.

Progress is read through `GET .../{planRunId}`; per-item statuses advance from `NOT_ATTEMPTED` to `APPLIED`/`FAILED`. The plan is capped at 800 items. At startup, any plan run left in `RUNNING` is marked `INTERRUPTED` with an audit event; recovery is explicit, never automatic.

## Persistence and restore

| Table | Purpose |
| --- | --- |
| `transformation_plan_run` | Plan/project, status, cancel flag, eligible/applied/failed/rolled-back/restored counts, timestamps |
| `transformation_plan_run_item` | Item order, item/run identifiers, status, error code and message |

`GET /api/v1/projects/{projectId}/migration-plans/{planId}/transformation-plan-runs` lists recent plan runs. `GET .../{planRunId}` returns the per-item result.

`POST .../{planRunId}/cancel` with confirmation is cooperative while the worker is active: it sets `cancelRequested`, the worker stops before the next item and performs the rollback. If no worker is active, the endpoint performs the same rollback directly and marks the header. A non-`RUNNING` run is refused.

`POST .../{planRunId}/resume` with confirmation accepts an `INTERRUPTED` run whose children are `APPLIED` or `NOT_ATTEMPTED`. It returns `202`, keeps the applied children, and the worker processes the first unattempted item onward. A failure or cancel during resume rolls back every applied child of the run, including those applied before the interruption. The header becomes `APPLIED` on success. The unknown-write window described for recovery also applies to resume: a file write that was never persisted as an applied child cannot be detected, so resume re-proposes the item against the current source and relies on the normal hash guards.

`POST .../{planRunId}/restore` with confirmation restores every child still in `APPLIED` in reverse order. A child that changed externally after apply is not overwritten; it becomes `ROLLBACK_FAILED`, and the plan run becomes `PARTIAL` or `ROLLBACK_FAILED`. Restore is refused for a `RUNNING`, `INTERRUPTED`, `RESTORED`, or fully rolled-back run.

`POST .../{planRunId}/recover` with confirmation handles an interrupted run. It is accepted only for `INTERRUPTED`, restores the children persisted as `APPLIED` in reverse order, and moves the header to `RECOVERED`, `PARTIAL`, or `ROLLBACK_FAILED`. A file write that the process performed after crashing but before persisting the child as `APPLIED` cannot be identified from the database and is not covered by recovery; this window is stated in the UI and API.

| Plan-run status | Meaning |
| --- | --- |
| `RUNNING` | Worker active, or a process that has not yet been marked at startup |
| `INTERRUPTED` | A previous process left the run incomplete; explicit recovery is required |
| `CANCELLED` | Cancellation stopped the run and restored tracked applied items |
| `APPLIED` | Every eligible item applied |
| `FAILED` | An item failed; all tracked applied items were restored |
| `ROLLBACK_FAILED` | At least one restore failed |
| `RESTORED` | A completed run was restored |
| `RECOVERED` | An interrupted run's tracked applied items were restored |
| `PARTIAL` | Only some restores succeeded |

## UI

The Migration plan view shows a coverage panel with eligible, rule-backed, unsupported, and applied-run counts, plus recent plan runs. The run button opens an explicit confirmation that names the eligible count and the rollback behavior. The result dialog polls the persisted run, shows per-item progress, offers cooperative cancel while `RUNNING`, recovery while `INTERRUPTED`, and restore while `APPLIED`. Three languages are supported.

## Verification

- Integration tests build a copied fixture plan, assert coverage counts and reasons, start a run asynchronously (`202`, `RUNNING`), poll to `APPLIED`, verify file changes, read the persisted history and detail, restore the run, and confirm byte-exact restoration.
- A write failure is injected by making a later item's descriptor directory read-only; the test polls to `FAILED`, asserts earlier items were applied and then rolled back, the later item failed, and the history permits no restore because nothing remains applied.
- Cancellation is tested on an orphaned `RUNNING` header with applied children; the endpoint rolls them back, marks `CANCELLED`, writes the audit event, and refuses a second cancel.
- Interruption is simulated by forcing a run to `RUNNING`, invoking the startup marker, asserting `INTERRUPTED`, recovering the tracked children, and refusing a second recovery.
- Resume is tested by restoring one applied child, marking it unattempted and the header `INTERRUPTED`, then resuming: applied children are skipped, the unattempted item is reapplied, the header returns to `APPLIED`, and a second resume is refused.
- A targeted run selects two eligible Java items: only their files change, a third eligible file stays byte-identical, the run reports two items, restore returns all files, and a non-eligible item in the request is rejected with `TRANSFORMATION_PLAN_ITEMS_INVALID`.
- The browser check captures the coverage panel, its item selection list, and the run confirmation in all three languages and both viewports.
- The end-to-end stage asserts executable-item and ineligible-reason counts through the packaged application.
