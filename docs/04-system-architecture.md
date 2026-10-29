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
  |-- Migration planning service
  |-- Transformation preview and guarded apply service
  |-- Report service
  |-- Internationalized API errors
  |
  +--> PostgreSQL relational database
  |
  +--> Allowlisted local source roots
  +--> External transformation backups
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

The graph endpoint caps a scan at 3000 relationships, ordered by relation type, evidence path, and line. It reports the true total in `relationCount`, the cap in `limit`, and sets `truncated` when the cap applied, so a partial graph is never presented as complete and the interface never hard-codes the number. Rendering keeps the browser responsive past the cap because the graph is drawn on a canvas with viewport culling and level-of-detail, so no DOM element is created per file, and label text stays crisp at any zoom because it is drawn in screen space. The paged relation table with type, certainty, and text filters remains the bounded entry point for exhaustive review. Measured on the shipped fixture, a scan of 29 files yields 26 relations and an 8 KB graph response, roughly 300 bytes per relation.

## Migration planning

Phase 3 adds a planning layer over a completed scan. It does not invoke the inventory parsers again and exposes no source-write operation.

- A plan is generated synchronously from persisted files, findings, technologies, and cross-file relations.
- Plan versions are immutable analysis snapshots; regeneration creates a new sequence, and scope exclusions update only the included flag and aggregate counts.
- Migration items reference their primary file, primary finding, all supporting findings, and the generating analyzer version.
- Dependencies reference the originating relation ID, type, evidence path, line, and certainty. Items without both endpoints are never connected.
- Phases are a topological projection of included dependencies. Cycles are surfaced as review-required items, not silently broken.
- The planning graph is capped at 800 items and 3000 dependency edges; true totals and the applied limits accompany the response.
- Planning status is limited to `READY`, `REVIEW_REQUIRED`, and `BLOCKED`. `TRANSFORMED`, `VALIDATED`, and `VALIDATION_FAILED` remain reserved for later phases.

The first vetted target profile is `SPRING_BOOT_MVC`: Spring MVC controllers, Spring DI, Spring JDBC data access, Spring message resources, and retained JSP views. Unsupported profiles are rejected.

## Transformation preview and guarded apply

Phase 4 adds nine vetted rules: seven Java rules, one properties rule, and one JSP rule. The Java rules are `SEASAR-DI-SPRING-DI` for Seasar2 component/binding annotations to Spring DI, `SASTRUTS-VALIDATION-BEAN` for plain SAStruts `@Required` to Jakarta `@NotNull`, `STRUTS-ACTIONFORM-MODEL` for simple ActionForm inheritance removal, `JAVAX-TO-JAKARTA` for supported namespace imports, `S2JDBC-QUERY-SPRING-JDBC` for a read-only select query chain, `S2TX-SPRING-TRANSACTIONAL` for a bare Seasar2 `@TransactionAttribute`, and `STRUTS-CONTROLLER-SKELETON` for an explicit Struts mapping and forward. The first six use JavaParser source ranges; the controller skeleton rule creates a separate `CREATE_FILE` artifact. `MESSAGE-RESOURCE-SPRING-UTF8` parses a Java properties bundle and rewrites non-ASCII Unicode escapes in message values as UTF-8 text. `JSP-BEAN-MESSAGE-JSTL` parses JSP tags and rewrites self-closing `bean:message` tags as JSTL `fmt:message`. No rule pretty-prints or rewrites unrelated code.

Proposal generation is read-only:

- It requires a current plan and a source hash equal to the scanned file.
- It persists an artifact type, target path, unified diff, original/proposed hashes, rule version, and syntax-validation result.
- It validates Java syntax for Java rules, properties syntax plus UTF-8 round-tripping for the message rule, and tag structure for the JSP rule.
- It supersedes earlier proposals for the same item.
- When several rules match an item, the lowest rule identifier wins, so the same item is always handled by the same rule regardless of bean registration order.

Apply and restore are separate guarded operations:

- Explicit confirmation is required.
- The current source hash must match the proposal or applied run.
- The rule version and proposed hash are rechecked before writing, by re-running the same rule over the same request the proposal was made from, including the persisted Struts routes, so a route-aware rule cannot produce a different outcome at apply time.
- For edits, the original bytes are copied to an external backup before an atomic single-file write.
- For creates, the target must not exist; the new file is written atomically without a backup.
- The write replaces bytes and nothing else: a replaced file keeps its own POSIX permissions, including the executable bit, and a created file takes the platform default for a new file. A temporary file is used only as the staging point for the atomic move, never as the delivered inode.
- A database failure triggers a best-effort byte restore or deletes the created file.
- Restore writes only from a verified backup for edits, and deletes only the generated file for creates; both refuse if the file changed after apply.
- The backup directory cannot be inside the project.

Automated validation is syntactic only. A proposal is never presented as semantically equivalent, compiled, or runtime-validated. The migration item's planning status is not upgraded by a transformation run.

Component, validation, form-model, Jakarta-namespace, read-only data-access, transaction, action-controller, message-resource, and JSP-view items are eligible for their respective rules. Controller method bodies, request binding, message APIs, message lifecycle, JSP form/error/custom tags, S2JDBC write statements, transaction-manager configuration, configuration conversion, and reports explicitly remain manual.

## Internationalization

Frontend interface strings live in locale JSON resources. The backend returns stable error codes, message keys, and argument arrays. Technical statuses and technology keys remain language-neutral.
