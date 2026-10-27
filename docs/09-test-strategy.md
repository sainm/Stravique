# Test Strategy

## Test pyramid

### Unit tests

- Path allowlist and symlink handling.
- File classifier and generated-file detection.
- Exclusion matching.
- Java AST framework rules.
- Secure XML/configuration parsing.
- JSP tag and form evidence.
- POM/Gradle declarations.
- File hash diff classification.
- Report serialization and CSV escaping.
- Localized Markdown titles and column labels with stable CSV and JSON field names.
- Technology certainty aggregation.

### Integration tests

- Isolated PostgreSQL test database and Flyway migration.
- Project creation through REST.
- Asynchronous scan completion using an injected synchronous executor.
- Evidence persistence and retrieval.
- Framework aggregate queries.
- Report counts matching persisted rows.
- Localized Markdown exports with locale-independent CSV and JSON field names.
- i18n error response keys in all three locales.

### Fixture tests

`sample-projects/legacy-web-demo` intentionally combines:

- A multi-module Maven build.
- SAStruts annotations and Struts imports.
- Seasar2 component and binding annotations.
- S2JDBC entity/DAO/query usage.
- dicon component definitions.
- JSP taglibs, forms, message/error tags.
- web.xml, properties, SQL, TLD, and static resources.
- A deliberate malformed XML fixture in test-only resources.
- A dynamic `Class.forName` call that remains unresolved/review-required.

The scanner test records source modification times and hashes before scanning and asserts no mutation afterward.

## API tests

Use MockMvc for successful and failure paths:

- Invalid source path.
- Disallowed scan root.
- Duplicate active scan.
- Cancellation state.
- Unknown project/file/report.
- Stable error code and message key under English, Chinese, and Japanese headers.

## Frontend tests

- Locale resource key parity.
- Status and certainty labels localize while technical values remain stable.
- Project creation validation.
- Empty/no-scan state.
- Evidence display with line labels.
- Report URL generation.
- Navigation and responsive class contracts.

## Visual verification

When a browser runtime is available:

- Render all Phase 1 routes at 1440 px and 390 px.
- Inspect English, Chinese, and Japanese navigation, tables, buttons, and error wrapping.
- Check evidence panes and status labels for overflow.
- Verify no locale produces clipped primary actions.

Persisted screenshots are verification artifacts, not product data.

## End-to-end tests

`scripts/e2e.sh` builds the runnable jar, starts it on a local port, and drives the real HTTP surface:

- Registers the shipped fixture and runs a scan to a terminal state.
- Downloads CSV, Markdown, and JSON exports and asserts the documented contract.
- Confirms CSV field names do not vary with `lang` or `Accept-Language`.
- Confirms Markdown titles and column labels follow the resolved language.
- Confirms localized API errors in Chinese and Japanese.
- Confirms an unscanned report still emits a complete header row.

It exists because MockMvc substitutes the servlet request. Locale resolution and response bytes are only trustworthy when observed through a real container.

## Verification commands

```bash
cd backend && ./gradlew test
cd frontend && npm test
cd frontend && npm run build
./scripts/e2e.sh
./verify.sh
```

`verify.sh` runs backend tests, frontend tests, production compilation, and the end-to-end stage.

## GitHub Actions

`.github/workflows/ci.yml` runs on Ubuntu with a disposable PostgreSQL 17 service. It creates the isolated test database, checks comment language, runs backend tests against Flyway migrations, installs frontend dependencies from the lockfile, runs frontend tests, builds both the SPA and standalone JAR, and runs the end-to-end stage against the packaged application.

## Exit criteria

- All automated suites pass.
- Fixture scan identifies all required file families.
- Framework evidence includes paths and lines.
- Parse failures and review items are recorded.
- Every report exports nonempty data only when persisted rows exist.
- The source fixture checksum and mtimes remain unchanged.
