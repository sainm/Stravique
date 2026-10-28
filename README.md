# Stravique

**Legacy System Inventory, Analysis & Modernization Platform**

Stravique is a local-first workbench for turning an existing Java web system into a traceable inventory. Phase 1 scans source without modifying it, records evidence down to file and line, separates declarations from analyzed use, and exports reports from persisted scan data.

The product deliberately starts with evidence and planning before broad automated migration. Its first responsibility is to help engineering teams understand what is actually present. Phase 4A adds one narrow, vetted transformation path with preview, confirmation, backup, and restore.

## Current status

Phase 1 acceptance plus the Phase 2 relations, Phase 3 migration-planning, and Phase 4A transformation-preview workspaces are implemented as a runnable local application:

- Multi-project registration with backend-enforced path policy.
- Read-only Maven/Gradle and Java web inventory.
- File classification for Java, JSP, HTML, JavaScript, CSS, XML, properties, YAML, dicon, SQL, TLD, POM, and Gradle files.
- Module, source, test, resource, web, configuration, and SQL root discovery.
- Evidence-linked Maven and Gradle dependencies between modules.
- JavaParser-based API evidence for SAStruts, Struts 1, Seasar2, S2JDBC, Servlet, JSTL, JUnit, and JDBC.
- Secure XML parsing with dicon component, scope, injection, lifecycle, and AOP evidence, Struts action mapping and message-resource evidence, and TLD/JSP tag-form evidence including Struts tag forms, fields, and message or error tags.
- Repeated-scan change classification using SHA-256.
- Asynchronous scan state, cancellation, restart recovery, parse-error records, and review items.
- Project-scoped audit history for registration, correction, scan, cancellation, failure, and export operations.
- English, Simplified Chinese, and Japanese interfaces.
- CSV, JSON, and Markdown reports generated from persisted rows.
- Project metadata and directory-role corrections.
- Evidence-linked migration planning with immutable versions, dependency phases, scope protection, and a review queue.
- A vetted Seasar2 DI → Spring DI proposal rule with persisted diff, syntax validation, explicit-confirmation apply, external backups, hash guards, and safe restore.
- Automated source-immutability, REST, parser, transformation-safety, localization, responsive, and browser visual checks.

## Architecture

```text
React + TypeScript + Vite
        |
        | /api/v1
        v
Spring Boot local backend
  |-- project and scan application services
  |-- read-only inventory engine
  |-- migration planning service
  |-- transformation preview and guarded apply service
  |-- PostgreSQL relational persistence
  |-- report service
  |
  +--> allowlisted local source directories
  +--> external transformation backups
```

The browser never scans the filesystem. The local backend resolves and validates every project path, does not follow symbolic links during inventory, disables external XML entities, and contains no source-write operation in the inventory API. Transformation apply/restore is a separate API that requires explicit confirmation and creates a backup first.

Detailed contracts are in [docs/](docs/).

## Requirements

- Java 21.
- Node.js 22 or newer.
- npm.
- PostgreSQL 16 or newer. PostgreSQL 18 is used for acceptance verification.
- macOS, Linux, or a compatible local environment. The scripts include a Homebrew Java 21 hint and also honor the default Java runtime.

## Dependency mirrors

The repository opts into domestic mirrors by default:

- npm: `https://registry.npmmirror.com` through `frontend/.npmrc`.
- Gradle distribution: Tencent Cloud Gradle mirror.
- Maven and Gradle plugins: Aliyun public and Gradle Plugin mirrors first.
- Official repositories remain available as fallbacks.

The lockfile records npmmirror tarball URLs. To intentionally override the package registry for one command, use:

```bash
cd frontend
npm ci --registry=https://registry.npmmirror.com
```

## Run locally

Start PostgreSQL and provision the local databases:

```bash
./scripts/ensure-postgres.sh
```

The local defaults are database `stravique`, test database `stravique_test`, and the current operating-system user as the PostgreSQL role. Override these through the `STRAVIQUE_DB_*` variables in [.env.example](.env.example).

```bash
./scripts/dev.sh
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173).

The script starts Spring Boot on `127.0.0.1:8080` and Vite on `127.0.0.1:5173`, waiting for the backend before opening the UI workflow.

If dependencies are already installed, the services can also be started separately:

```bash
cd backend
./gradlew bootRun
```

The wrapper is pinned to Gradle 8.14.6. The project scripts use it first and fall back to a system Gradle installation only if the wrapper is unavailable.

```bash
cd frontend
npm run dev
```

## Scan roots

By default, Stravique allows the backend working directory, nearby `sample-projects` directories, and the backend working directory's parent. This default is intentionally narrow.

To scan projects elsewhere, configure one or more absolute roots:

```bash
export STRAVIQUE_ALLOWED_SCAN_ROOTS="/workspace/projects,/Volumes/archive/java"
cd backend
./gradlew bootRun
```

Use commas between roots. A project must resolve to a directory at or below one of these roots. Filesystem roots, the exact home directory, missing paths, and paths outside the policy are rejected before discovery.

Additional runtime controls:

| Variable | Default | Purpose |
| --- | --- | --- |
| `STRAVIQUE_ALLOWED_SCAN_ROOTS` | local defaults | Comma-separated read roots |
| `STRAVIQUE_MAX_FILE_BYTES` | `2097152` | Per-file analysis limit |
| `STRAVIQUE_MAX_FILES` | `100000` | Scan file-count limit |
| `STRAVIQUE_ANALYZER_VERSION` | `1.0.0` | Persisted analyzer version |
| `STRAVIQUE_DEFAULT_LOCALE` | `en-US` | Fallback for interface messages and exports when the request states no language |
| `SERVER_PORT` | `8080` | Backend port |
| `STRAVIQUE_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/stravique` | Runtime database |
| `STRAVIQUE_DB_USER` | Current OS user | PostgreSQL role |
| `STRAVIQUE_DB_PASSWORD` | Empty | PostgreSQL password |
| `STRAVIQUE_TEST_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/stravique_test` | Isolated test database |
| `STRAVIQUE_TRANSFORMATION_BACKUP_DIR` | `~/.stravique/transformation-backups` | External backup directory for applied transformations; must be outside the project |

## Sample project

`sample-projects/legacy-web-demo` is a deliberately mixed legacy fixture. It contains:

- A three-module Maven hierarchy.
- SAStruts actions and Struts 1 tag usage.
- Seasar2 component, binding, dicon, and lifecycle configuration.
- An S2JDBC entity, DAO, and query calls.
- JSP, JSTL, TLD, web.xml, properties, YAML, SQL, static assets, and tests.
- Generated paths and generated-file headers that must be skipped.
- A dynamic class load that must remain review-required.
- A malformed XML file that must be recorded as a parse failure.

Register the directory in the UI or use the API:

```bash
curl -X POST http://127.0.0.1:8080/api/v1/projects \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Legacy Web Demo",
    "sourcePath": "/absolute/path/to/Stravique/sample-projects/legacy-web-demo"
  }'
```

Start a scan with the returned project ID:

```bash
curl -X POST http://127.0.0.1:8080/api/v1/projects/<project-id>/scans
```

After the scan completes, generate a migration plan:

```bash
curl -X POST http://127.0.0.1:8080/api/v1/projects/<project-id>/migration-plans \
  -H 'Content-Type: application/json' \
  -d '{"targetProfile":"SPRING_BOOT_MVC"}'
```

Planning reads persisted scan rows only. It does not modify the fixture or generate patches.

## Certainty model

| State | Meaning |
| --- | --- |
| `IDENTIFIED` | A declaration or artifact is directly visible; usage is not established. |
| `ANALYZED` | A supported static parser observed the construct. |
| `INFERRED` | A rule suggests a relationship requiring context. |
| `REVIEW_REQUIRED` | Human review is needed before relying on the result. |
| `UNRESOLVED` | Dynamic or unsupported behavior prevents a reliable answer. |
| `PARSE_FAILED` | The configured parser could not produce a trustworthy result. |

A Maven dependency is not upgraded to runtime use. A parsed Java import is not proof that a branch executes. Dynamic loading and reflection remain explicit review items.

## Persistence and reports

Phase 1 uses PostgreSQL as the only database. Flyway owns the schema. Each scan creates immutable rows for modules, files, evidence, and technology aggregates.

Reports are available from the UI and at:

```text
GET /api/v1/projects/{projectId}/reports/{type}.{format}
```

Types: `FILES`, `STRUCTURE`, `TECHNOLOGY`, `FRAMEWORKS`, `RELATIONS`, `CONFIGURATION`, `PARSE_ERRORS`, and `REVIEWS`.

Formats: `csv`, `json`, and `markdown`.

Exports accept an optional `lang` parameter. Language resolves from `lang`, then `Accept-Language`, then `STRAVIQUE_DEFAULT_LOCALE`. Only Markdown localizes its title and column labels; CSV and JSON keep stable English field names so a parser never breaks when the interface language changes. Cell values stay language-neutral, so a status such as `ANALYZED` reads the same in every language. CSV output always includes a header row, even for an empty report, and starts with a UTF-8 byte order mark.

## Verify

```bash
./verify.sh
```

The verification command checks:

- No Chinese comment text in source comments.
- PostgreSQL-backed Flyway migration, JPA validation, API, parser, localization, report, and source-immutability tests.
- Frontend locale-key parity, status localization, language switching, and responsive contracts.
- TypeScript compilation and production Vite build.
- A packaged-application end-to-end run that scans the fixture over HTTP, generates a migration plan, and asserts the export, planning, dependency, and scope-guard contracts.
- Transformation unit and integration tests on an isolated fixture copy, including preview, confirmation, backup, apply, restore, changed-source rejection, and shipped-fixture immutability.

The end-to-end stage starts the built application on a free local port, runs a full scan, and checks the bytes a client receives. It can also be run on its own:

```bash
./scripts/e2e.sh
```

Set `STRAVIQUE_E2E_PORT` to change the port. The stage needs the runtime database from `./scripts/ensure-postgres.sh` and the fixture project.

With the application running and Google Chrome installed, the multilingual browser layout check is:

```bash
cd frontend
npm run test:visual
```

Screenshots and the machine-readable visual report are written to `frontend/.artifacts/visual/`, which is ignored as verification output.

## Build a standalone package

```bash
./scripts/build.sh
java -jar backend/build/libs/stravique-backend-0.1.0.jar
```

The frontend production build is copied into the Spring Boot static resources. SPA routes under `/projects` fall back to `index.html`.

## Phase boundary

Implemented:

- Inventory.
- Static framework and configuration evidence.
- Basic technology aggregation.
- Evidence-linked Maven and Gradle module dependencies.
- Initial Phase 2 cross-file relations for Java, Seasar2 injection, configuration references, Struts/SAStruts/JSP flows, DAO/SQL candidates, and message lifecycle.
- A Relations workspace with persisted counts, filters, certainty, and source/target navigation.
- A table/map switch where graph nodes and edges are aggregated only from persisted relationships.
- Evidence-derived Phase 3 migration planning with immutable versions, risk, automation, priority, evidence links, scope protection, and dependency phases.
- A Migration workspace with item lists, phase lanes, a persisted dependency map, review queue, and plan history.
- Phase 4A Seasar2 DI → Spring DI preview with persisted diffs, syntax validation, guarded single-file apply, external backups, execution records, and restore.
- Review visibility.
- Reports.

Not implemented:

- Struts/SAStruts → Spring MVC transformation.
- S2JDBC, message, validation, XML, dicon, JSP, or TLD transformation.
- Compile or runtime validation.
- Batch execution across a plan.
- Graph database projections.
- AI conclusions treated as evidence.

Phase 3 planning status is limited to `READY`, `REVIEW_REQUIRED`, and `BLOCKED`. A planned item is never presented as transformed or validated. Phase 4A persists transformation proposals and runs separately and requires explicit confirmation before writing a file.
