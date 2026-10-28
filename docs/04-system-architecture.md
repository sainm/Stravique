# System Architecture

## Runtime topology

```text
Browser
  |
  | HTTP / JSON / downloads
  v
React + Vite frontend
  |
  | /api/v1
  v
Spring Boot local backend (loopback by default)
  |-- Project and scan application services
  |-- Read-only inventory engine
  |-- Report service
  |-- Internationalized API errors
  |
  +--> PostgreSQL relational database
  |
  +--> Allowlisted local source roots (read-only)
```

The backend owns filesystem access. The browser never receives unrestricted directory-reading capability.

## Frontend

- React and TypeScript.
- Vite development and production builds.
- React Router for project-scoped routes.
- TanStack Query for server state and scan polling.
- i18next and react-i18next for all interface text.
- Lucide line icons.
- A lightweight custom evidence viewer in Phase 1.
- The relation graph is drawn on a canvas with viewport culling and level-of-detail, not as one DOM element per file.

UI state is not treated as evidence. After a scan transition, affected queries are invalidated and reload persisted server data.

## Backend layers

```text
api
  controllers, request/response DTOs, problem details
application
  project lifecycle, scan orchestration, reporting
domain
  entities, certainty semantics, scan state transitions
inventory
  path policy, discovery, classifiers, parsers, rules, aggregation
infrastructure
  repositories, persistence, audit, runtime configuration
```

Dependencies point inward. Inventory parsers emit findings and never mutate project sources.

## Scan flow

1. Create a persisted scan record in `QUEUED`.
2. Validate the canonical source path against configured roots.
3. Open a directory walk with no symlink following.
4. Discover and classify supported files, apply exclusions and size limits.
5. Identify build modules and structural roots.
6. Decode and parse each supported file.
7. Emit evidence-linked findings and technology aggregates.
8. Compare file hashes with the previous completed scan.
9. Persist counts and finish as `COMPLETED`, `FAILED`, or `CANCELLED`.
10. Invalidate query caches in the UI through polling.

Cancellation is cooperative. A cancel request moves a running scan to `CANCEL_REQUESTED`; the worker persists `CANCELLED` at the next bounded checkpoint.

## File access policy

- Default roots are the backend working directory and nearby sample-project directories.
- Additional roots are supplied through `STRAVIQUE_ALLOWED_SCAN_ROOTS`.
- Both requested paths and allowlist roots are normalized through `toRealPath`.
- A requested path must be equal to or below an allowed root, except known sensitive roots and home roots.
- Source path symlinks are resolved before policy evaluation.
- Files are discovered without following links.
- File content size is capped.
- XML parsing disables external entities and DTDs.
- Scanner code has no write API for project sources.

## Persistence

Phase 1 uses PostgreSQL for canonical project, scan, file, evidence, technology, and audit records. Flyway owns the schema and all migrations use PostgreSQL native types.

## Reporting

Reports query scan-scoped persisted rows. CSV, JSON, and Markdown serializers share DTOs and counts. Export operations are audited.

## Relations

A scan derives cross-file relations from static evidence only. Each row records a source file, a target file, a relation type, the evidence path and line, the rule that matched, its certainty, and the analyzer version. A unique constraint over scan, endpoints, type, evidence path, and line keeps repeated scans from duplicating a relation.

Relation types are: `JAVA_IMPORT`, `JAVA_INHERITANCE`, `SEASAR_BINDING`, `SEASAR_DICON_INJECTION`, `CONFIG_CLASS_REFERENCE`, `STRUTS_ACTION_TO_JSP`, `JSP_FORM_TO_ACTION`, `SASTRUTS_ACTION_TO_JSP`, `DAO_SQL_REFERENCE`, `MESSAGE_PRODUCTION`, and `MESSAGE_DISPLAY`.

Relations are scan-scoped and cascade with the scan row. Every read resolves the scan through its owning project, so a scan identifier alone cannot expose another project's rows. A relation is evidence of a static reference, never proof that a branch executes.

The graph endpoint caps a scan at 3000 relationships, ordered by relation type, evidence path, and line. It reports the true total in `relationCount` and sets `truncated` when the cap applied, so a partial graph is never presented as complete. Rendering keeps the browser responsive past the cap because the graph is drawn on a canvas with viewport culling and level-of-detail, so no DOM element is created per file, and label text stays crisp at any zoom because it is drawn in screen space. The paged relation table with type, certainty, and text filters remains the bounded entry point for exhaustive review. Measured on the shipped fixture, a scan of 29 files yields 26 relations and an 8 KB graph response, roughly 300 bytes per relation.

## Internationalization

Frontend interface strings live in locale JSON resources. The backend returns stable error codes, message keys, and argument arrays. Technical statuses and technology keys remain language-neutral.
