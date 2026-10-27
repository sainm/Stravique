# API Design

## Conventions

- Base path: `/api/v1`.
- JSON requests and responses.
- UUID path identifiers.
- Stable state names in English.
- Pagination uses `page`, `size`, `totalElements`, and `totalPages`.
- API errors use a stable `code`, `messageKey`, localized `message`, `args`, timestamp, and request ID.
- `Accept-Language` supports `en-US`, `zh-CN`, and `ja-JP`.

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
| `GET` | `/files/{fileId}` | File detail and evidence |
| `GET` | `/projects/{id}/tree` | Directory tree for latest scan |
| `GET` | `/projects/{id}/module-dependencies` | Maven/Gradle dependencies between scanned modules |
| `GET` | `/projects/{id}/findings` | Filtered findings |
| `GET` | `/projects/{id}/frameworks` | Technology aggregates |
| `GET` | `/projects/{id}/frameworks/{key}` | Framework detail and evidence |
| `GET` | `/projects/{id}/reviews` | Review-required, unresolved, and failed items |

## Reports

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects/{id}/reports` | Available reports and row counts |
| `GET` | `/projects/{id}/reports/{type}.{format}` | Persisted-data export |

Formats: `csv`, `json`, `markdown`.

Report types: `files`, `structure`, `technology`, `frameworks`, `configuration`, `parse-errors`, `reviews`.

## Status codes

- `200`: successful read/update.
- `201`: project or scan created.
- `202`: asynchronous scan accepted (response includes its persisted scan ID).
- `400`: malformed input or invalid metadata.
- `404`: project, scan, file, or report is absent.
- `409`: source path conflict, concurrent scan, or invalid terminal transition.
- `422`: path fails read/allowlist policy.
- `500`: unexpected failure; no source content is included in the error.
