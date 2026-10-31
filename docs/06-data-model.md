# Data Model

## Core entities

| Entity | Key fields | Purpose |
| --- | --- | --- |
| `analysis_project` | id, name, source_path, metadata, exclusions, last_scan_at | Registered local project |
| `path_override` | project_id, relative_path, role | User correction for uncertain directory structure |
| `scan_run` | project_id, sequence, status, counts, timestamps, analyzer_version | Immutable scan job record |
| `project_module` | scan_id, project_id, relative_path, name, source/test/web/resource roots | Module snapshot for one scan |
| `module_dependency` | scan_id, from_module_id, to_module_id, evidence path and line | Maven or Gradle dependency between scanned modules |
| `project_file` | scan_id, module_id, relative_path, metadata, hash, change_type | File snapshot for one scan |
| `analysis_finding` | scan_id, file_id, kind, object_key, range, evidence, rule, certainty | Traceable static/config observation |
| `analysis_relation` | scan_id, source/target file and object, type, evidence path/line, certainty | Evidence-backed cross-file relation |
| `technology_usage` | scan_id, technology_key, category, status, evidence counts | Technology aggregate for a scan |
| `migration_plan` | project_id, scan_id, sequence, source/target profile, counts | Immutable planning snapshot for one scan |
| `migration_item` | plan_id, item_key, type, target, reason, risk, automation, status, phase, included | Ordered, evidence-linked migration work item |
| `migration_item_finding` | item_id, finding_id, role | Primary or supporting analysis evidence for an item |
| `migration_dependency` | plan_id, from/to item, relation_id, type, evidence path/line, certainty | Item dependency derived from a persisted relation |
| `transformation_proposal` | plan/item/file, rule/version, hashes, diff, validation status | Read-only diff proposal for a vetted rule |
| `transformation_plan_run` | plan/project, status, cancel flag, eligible/applied/failed/rolled-back/restored counts, timestamps | Persisted plan-wide transformation run |
| `transformation_plan_run_item` | plan run, item/run identifiers, order, status, error | Per-item plan-run outcome |
| `transformation_run` | proposal/item/file, status, backup path, hashes, rule/version | Explicit apply or restore audit record |
| `audit_event` | project_id, action, entity_type, entity_id, detail, occurred_at | Project-scoped operation trace |

## Scan state

```text
QUEUED -> RUNNING -> COMPLETED
                  -> FAILED
                  -> CANCEL_REQUESTED -> CANCELLED
QUEUED ----------------------------> CANCELLED
```

Terminal states cannot return to running. Every retry creates a new `scan_run`.

## File state

| Field | Values |
| --- | --- |
| scan status | `DISCOVERED`, `ANALYZED`, `SKIPPED`, `PARSE_FAILED` |
| parse status | `NOT_APPLICABLE`, `PARSED`, `PARTIAL`, `FAILED` |
| change type | `ADDED`, `MODIFIED`, `UNCHANGED`, `DELETED` |
| generated | Boolean |

Deleted files retain previous path metadata and are marked `DELETED`; source contents are not fabricated.

## Technology certainty

Technology aggregates derive their status without upgrading evidence:

- API or configuration evidence exists: `ANALYZED`.
- Only a build dependency exists: `IDENTIFIED`.
- Convention or weak textual relationship exists: `INFERRED`.
- Dynamic/unsupported condition affects interpretation: `REVIEW_REQUIRED` or `UNRESOLVED`.
- Parser failure prevented analysis: `PARSE_FAILED`.

A technology can have API evidence and still be marked review-required when its runtime activation is unknown.

## Finding fields

- Stable UUID.
- Project, scan, module, and file identifiers.
- Relative file path snapshot.
- Start and end line.
- Analysis kind and object key.
- Rule ID.
- Human-readable evidence summary and source snippet.
- Certainty state.
- Analyzer version and timestamp.
- Parse status.
- Optional structured metadata JSON.

## Evidence rules

- Source snippets are bounded in size.
- A finding points to the smallest practical line range.
- A finding never claims a call executes.
- XML/configuration syntax findings use secure parser success plus source locator evidence.
- Module dependencies reference both module endpoints and the build-file declaration line.
- Relationships across files are not introduced in Phase 1 merely to make the UI look complete.

## Migration planning model

A `migration_plan` references exactly one completed scan and one target profile. It is created by a deterministic rule pass over persisted rows; generation never calls a source parser and never writes project files.

A `migration_item` carries:

- A plan-scoped deterministic key.
- Item type, target key, title, primary file and primary finding.
- Automation level, risk level, priority, phase, and planning status.
- A reason code explaining the disposition.
- Counting fields for linked findings, dependencies, and dependents.
- An `included` flag for scope adjustment.

Only these planning statuses are emitted in Phase 3:

- `READY`
- `REVIEW_REQUIRED`
- `BLOCKED`

`TRANSFORMED`, `VALIDATED`, and `VALIDATION_FAILED` are reserved for later transformation phases.

Dependencies are directed from a dependent item to its prerequisite (`from_item_id` depends on `to_item_id`). Each row keeps the originating relation ID, relation type, evidence path, start line, and certainty. A dependency is created only when both relation endpoints map to different items.

Phases are computed over included items with a topological pass. A cycle is preserved as review-required evidence rather than hidden.

Plan versions are never rewritten. A newer scan makes older plans `stale`; the caller regenerates a new version.

## Transformation model

A `transformation_proposal` is a read-only diff snapshot:

- Plan, item, and source-file identifiers.
- Rule ID and rule version.
- Source, target, and proposed SHA-256 values. `originalSha256` always describes the item source; `targetSha256` describes the file apply will write and is null for `CREATE_FILE`. For a same-file edit the two hashes describe the same file; for a cross-file edit they describe the item source and a different target file.
- Unified diff.
- Syntax-validation status and message.
- `PROPOSED`, `APPLIED`, `RESTORED`, or `SUPERSEDED`.

A `transformation_run` records an explicit write:

- Proposal, item, and source file.
- Rule ID and version.
- Original, target, and applied hashes.
- Backup path outside the project.
- `APPLIED`, `RESTORED`, or `FAILED`.

Apply requires confirmation, a current plan, matching current source and target hashes, rule-version match, and re-derivation of the proposed hash. For a cross-file edit the target is backed up and written while the item source is never written. Restore requires confirmation, an `APPLIED` run, matching source and applied hashes, and a verified backup. Neither operation changes the migration item's planning status.

A plan run groups the per-item runs produced by one confirmed plan-wide action. Its header records `RUNNING`, `INTERRUPTED`, `CANCELLED`, `APPLIED`, `FAILED`, `ROLLBACK_FAILED`, `RESTORED`, `RECOVERED`, or `PARTIAL`, a cooperative `cancelRequested` flag, and aggregate counts. An `INTERRUPTED` run can be resumed: applied children stay, unattempted children run, and a failure or cancel rolls back every applied child of the run. Each child records the plan order, item and run identifiers, `APPLIED`, `FAILED`, `ROLLED_BACK`, `ROLLBACK_FAILED`, `NOT_ATTEMPTED`, or `RESTORED`, and the localized failure. Coverage is computed live from persisted items, rules, files, proposals, and runs; it is not stored.

## Identifiers and time

Entity IDs are UUIDs. API timestamps are ISO-8601 UTC values. UI formatting localizes presentation without changing stored values. Technical enums remain invariant across locales.
