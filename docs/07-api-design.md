# API Design

## Conventions

- Base path: `/api/v1`.
- JSON requests and responses.
- UUID path identifiers.
- Stable state names in English.
- Pagination uses `page`, `size`, `totalElements`, and `totalPages`.
- API errors use a stable `code`, `messageKey`, localized `message`, `args`, timestamp, and request ID.
- `Accept-Language` supports `en-US`, `zh-CN`, and `ja-JP`.

## Language resolution

Interface and export language resolve in this order:

1. The `lang` query parameter, when present and supported.
2. The `Accept-Language` request header, first supported match in client quality order.
3. `STRAVIQUE_DEFAULT_LOCALE`, which defaults to `en-US`.

An explicit `lang` value that is unsupported falls through to the header and then to the configured default rather than returning an error, so a stale bookmark never fails. Regional variants narrow to the supported language: `zh-TW` and `zh_CN` resolve to `zh-CN`, `en-GB` resolves to `en-US`. The frontend sends `lang` on export links because a plain `<a download>` navigation cannot control every browser's `Accept-Language`.

The configured default is authoritative when the request carries no `Accept-Language` header. The servlet container reports its own locale for such requests, so the resolver ignores the container value in that case.

## Runtime

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/runtime` | Allowed scan roots, limits, analyzer version, default locale |

## Projects

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects` | Project registry |
| `POST` | `/projects` | Register and validate a project |
| `GET` | `/projects/{id}` | Project summary |
| `PUT` | `/projects/{id}` | Correct metadata and exclusions |
| `GET` | `/projects/{id}/overview` | Derived current scan summary |
| `GET` | `/projects/{id}/path-overrides` | Directory-role corrections |
| `PUT` | `/projects/{id}/path-overrides` | Replace directory-role corrections |
| `GET` | `/projects/{id}/audit-events` | Paged project audit history |

## Scans

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/projects/{id}/scans` | Queue a read-only scan |
| `GET` | `/projects/{id}/scans` | Scan history |
| `GET` | `/scans/{scanId}` | Scan status and counts |
| `POST` | `/scans/{scanId}/cancel` | Cooperatively cancel |

## Inventory

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects/{id}/files` | Paged files with search and filters |
| `GET` | `/files/{fileId}` | File detail, evidence, and incoming/outgoing cross-file relations |
| `GET` | `/projects/{id}/tree` | Directory tree for latest scan |
| `GET` | `/projects/{id}/module-dependencies` | Maven/Gradle dependencies between scanned modules |
| `GET` | `/projects/{id}/findings` | Filtered findings |
| `GET` | `/projects/{id}/frameworks` | Technology aggregates |
| `GET` | `/projects/{id}/frameworks/{key}` | Framework detail and evidence |
| `GET` | `/projects/{id}/relations` | Paged and filtered cross-file relationships |
| `GET` | `/projects/{id}/relations/overview` | Relationship totals by type and certainty |
| `GET` | `/projects/{id}/relations/graph` | Aggregated nodes and edges derived only from persisted relationships |
| `GET` | `/projects/{id}/reviews` | Review-required, unresolved, and failed items |

The graph response carries `relationCount`, the true total under the current filters, `limit`, the cap the endpoint applied, and `truncated`, which is `true` when only the first `limit` relationships were returned in relation type, evidence path, and line order. Aggregated nodes and edges are always computed from the returned rows, so a truncated graph stays internally consistent; `relationCount` still reports what the scan actually holds, so a partial graph is never mistaken for a complete one. Clients should surface the flag, state the cap from `limit` rather than hard-coding it, and offer the paged relation table for exhaustive review.

## Migration planning

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects/{id}/migration-plans` | Plan history, newest first, with staleness |
| `POST` | `/projects/{id}/migration-plans` | Generate a plan from a completed scan |
| `GET` | `/projects/{id}/migration-plans/{planId}` | Plan summary and phase counts |
| `GET` | `/projects/{id}/migration-plans/{planId}/items` | Filtered, paged migration items |
| `GET` | `/projects/{id}/migration-plans/{planId}/items/{itemId}` | Item evidence and dependencies |
| `PATCH` | `/projects/{id}/migration-plans/{planId}/items/{itemId}` | Include or exclude an item |
| `GET` | `/projects/{id}/migration-plans/{planId}/graph` | Dependency graph from persisted relations |

Plan generation accepts optional `scanId` and `targetProfile`. With no `scanId`, the latest `COMPLETED` scan is used. The only supported target profile is `SPRING_BOOT_MVC`.

Plans are immutable analysis snapshots; each `POST` creates a new per-project sequence. Excluding an item that another included item depends on is rejected with `MIGRATION_DEPENDENCY_CONFLICT` and the count of included dependents. Re-including an item whose prerequisite is excluded is rejected with `MIGRATION_PREREQUISITE_EXCLUDED`. Excluded items remain visible for traceability.

The migration graph is capped at 800 items and 3000 dependency edges. `itemCount` and `edgeCount` report true included totals; `itemLimit`, `edgeLimit`, and `truncated` state the boundary. Graph edges point from prerequisite to dependent item.

Phase 3 only emits `READY`, `REVIEW_REQUIRED`, and `BLOCKED`. The other migration item statuses are reserved for later phases.

## Transformation preview

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects/{id}/migration-plans/{planId}/items/{itemId}/transformation-proposals` | Proposal history for one item |
| `POST` | `/projects/{id}/migration-plans/{planId}/items/{itemId}/transformation-proposals` | Generate a read-only preview |
| `GET` | `/projects/{id}/migration-plans/{planId}/items/{itemId}/transformation-runs` | Execution and restore history |
| `POST` | `/projects/{id}/transformation-proposals/{proposalId}/apply` | Apply a proposal with explicit confirmation |
| `POST` | `/projects/{id}/transformation-runs/{runId}/restore` | Restore an applied file from backup |

The only vetted rule is `SEASAR-DI-SPRING-DI` for `COMPONENT_BEAN` items targeting `SPRING_DI`. Proposal generation returns `422` for an item without a vetted rule. Apply and restore require `{ "confirmed": true }`; missing confirmation returns `400`. A proposal stores the diff, original/proposed hashes, rule/version, and syntax-validation result. Apply refuses a stale plan, a changed source file, a superseded proposal, or a result that no longer matches the stored hash. Restore refuses if the file changed after apply. The original file is backed up outside the project; the backup path is not returned by the API.

## Reports

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects/{id}/reports` | Available reports and row counts |
| `GET` | `/projects/{id}/reports/{type}.{format}` | Persisted-data export |

Formats: `csv`, `json`, `markdown`.

Report types: `files`, `structure`, `technology`, `frameworks`, `relations`, `configuration`, `parse-errors`, `reviews`.

Exports accept optional `scanId` and `lang` query parameters. The export inherits the language resolution described above.

Localization applies to the Markdown document only:

| Format | Titles and column headers | Cell values |
| --- | --- | --- |
| `csv` | Stable English field names | Unchanged |
| `markdown` | Localized | Unchanged |
| `json` | Stable English field names | Unchanged |

CSV and JSON share the same stable English field names, so a downstream parser never breaks when a user switches language; only Markdown renders a localized title and localized column labels. Cell values stay language-neutral as well, so a status such as `ANALYZED` or a file type such as `JSP` reads the same in every locale.

A CSV export always writes its header row, including when the report has no rows, so an empty result appears as a header-only file rather than a zero-byte download. Output starts with a UTF-8 byte order mark because cell values can contain local text and spreadsheet tools need it to select the correct encoding.

Report types, formats, and report counts do not change with language.

## Status codes

- `200`: successful read/update.
- `201`: project or scan created.
- `202`: asynchronous scan accepted (response includes its persisted scan ID).
- `400`: malformed input or invalid metadata.
- `404`: project, scan, file, or report is absent.
- `409`: source path conflict, concurrent scan, invalid terminal transition, missing completed scan for planning, an item still required by included items, a stale transformation plan, or a source file that changed after the proposal.
- `422`: path fails read/allowlist policy, the migration target profile is unsupported, or no vetted transformation rule supports the item.
- `500`: unexpected failure; no source content is included in the error.
