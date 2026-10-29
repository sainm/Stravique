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
- Report message coverage derived from the report declarations, so a new type or column cannot ship without labels or leave orphaned keys.
- Technology certainty aggregation.
- Migration plan graph adaptation and prerequisite-before-dependent layout.
- Seasar2 DI → Spring DI AST-range rule: imports, component/binding annotations, supported scopes, formatting preservation, and rejected constructs.
- SAStruts `@Required` → Bean Validation `@NotNull` rule and its rejected attribute forms.
- Struts ActionForm inheritance removal rule, including lifecycle-method and residual-reference rejection.
- Supported `javax.*` → `jakarta.*` import rule, including wildcard imports and unsupported-package refusal.
- Read-only S2JDBC `selectBySql` → JdbcTemplate rule, including rejection of update calls and unsupported manager usage.
- Struts controller skeleton rule: explicit-mapping generation, package and forward filtering, no invented routes, and supported item/target.
- Message bundle UTF-8 rule: non-ASCII escape conversion, comment and continuation preservation, surrogate pairs, rejected malformed bundles, and unchanged message maps.
- JSP `bean:message` → JSTL rule: taglib reuse and insertion, unused taglib removal, rejected attribute and body forms, and unbalanced tag structure rejection.
- Atomic source replacement: a replaced file keeps its own permissions and executable bit, a created file takes the platform default rather than the private mode of a staging file, a shorter replacement truncates, and no staging file survives.
- Paged query ordering ends at the row identifier and never repeats a column, so offset paging cannot duplicate or drop rows that share a sort key.

### Integration tests

- Isolated PostgreSQL test database and Flyway migration.
- Project creation through REST.
- Asynchronous scan completion using an injected synchronous executor.
- Evidence persistence and retrieval.
- Framework aggregate queries.
- Report counts matching persisted rows.
- Localized Markdown exports with locale-independent CSV and JSON field names.
- i18n error response keys in all three locales.
- Migration plan generation from a completed scan, item type coverage, evidence links, phases, dependency counts, graph limits, and staleness.
- Scope guards: excluding a required prerequisite and re-including an item with an excluded prerequisite are both rejected.
- Transformation preview, apply, and restore on an isolated fixture copy for DI, validation, form-model, Jakarta-namespace, and read-only S2JDBC rules, including confirmation, backup, hash, and changed-source guards, and the applied and restored file keeping its original permissions.
- Create-only controller skeleton proposal, apply, run record, and restore on the isolated fixture copy: the target must not exist, the action source stays byte-identical, and restore removes the generated file.
- Message bundle proposal, UTF-8 apply, and byte-exact restore on the isolated fixture copy, with unchanged keys and placeholders.
- JSP message-tag proposal, apply, taglib insertion, unused directive removal, and byte-exact restore on the isolated fixture copy.
- The shipped fixture remains byte-identical after transformation tests.

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
- Unknown project/file/report/plan/item.
- Missing completed scan and unsupported migration target profile.
- Migration scope conflicts in both directions.
- Stable error code and message key under English, Chinese, and Japanese headers.

## Frontend tests

- Locale resource key parity.
- Status and certainty labels localize while technical values remain stable.
- Project creation validation.
- Empty/no-scan state.
- Evidence display with line labels.
- Cross-file relationship filters, counts, and source/target navigation.
- Relation graph layout: layered depth, sibling stacking, cycles, isolated nodes, and inferred propagation.
- Migration dependency graph conversion and prerequisite-before-dependent layers.
- Report URL generation.
- Navigation and responsive class contracts.

## Visual verification

When a browser runtime is available:

- Render the project routes, including migration plan, review, and dependency map, at 1440 px and 390 px.
- Inspect English, Chinese, and Japanese navigation, tables, buttons, and error wrapping.
- Check evidence panes and status labels for overflow.
- Check relation filters and source/target rows for overflow.
- Check migration item rows, plan metrics, and the migration dependency canvas for content and overflow.
- Open every vetted-rule inspector, including the create-only controller skeleton with its new-file warning and diff.
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
- Confirms the relations report contains the same persisted relationship count shown by analysis APIs.
- Generates a migration plan over HTTP, asserts evidence-derived item types and dependency edges, and confirms the scope guard rejects excluding a required prerequisite.

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
