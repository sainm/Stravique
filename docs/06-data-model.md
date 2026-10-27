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

## Identifiers and time

Entity IDs are UUIDs. API timestamps are ISO-8601 UTC values. UI formatting localizes presentation without changing stored values. Technical enums remain invariant across locales.
