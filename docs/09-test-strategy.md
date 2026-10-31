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
- Seasar2 DI → Spring DI AST-range rule: imports, component/binding annotations, prototype/request/session/application → `@Scope`, singleton omission, formatting preservation, and rejected constructs.
- SAStruts `@Required` → Bean Validation `@NotNull` and length annotations → `@Size`, including import merge/removal, rejected attribute forms, duplicate annotations, conflicting imports, and non-integer values.
- Struts ActionForm inheritance removal rule, including lifecycle-method and residual-reference rejection.
- Supported `javax.*` → `jakarta.*` import rule, including wildcard imports and unsupported-package refusal.
- S2JDBC `selectBySql` and insert/update/delete writes → JdbcTemplate rule, including the expanded `java.sql.Types` table, rejection of unmapped types and mismatched parameter counts, and unsupported manager usage.
- Seasar2 `@TransactionAttribute` → Spring `@Transactional` rule: bare and REQUIRED/REQUIRES_NEW propagation, propagation import reuse, and rejection of other values, non-enum values, duplicate `@Transactional`, a missing import, and an import with no annotation.
- Struts controller skeleton rule: explicit-mapping generation, package and forward filtering, no invented routes, and supported item/target.
- Message bundle UTF-8 rule: non-ASCII escape conversion, comment and continuation preservation, surrogate pairs, rejected malformed bundles, and unchanged message maps.
- JSP tag rule: `bean:message` conversion, `bean:write` conversion, taglib reuse and insertion, unused taglib removal, legacy JSTL URI modernization, rejected attribute/body forms and unknown URIs, and unbalanced tag structure rejection.
- `web.xml` Jakarta namespace rule: Java EE and J2EE namespace/schema/version conversion, already-Jakarta and unsupported-version rejection, malformed XML, and file-name support.
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
- Transformation preview, apply, and restore on an isolated fixture copy for DI/scope, validation, form-model, Jakarta-namespace, S2JDBC, and transaction rules, including confirmation, backup, hash, and changed-source guards, and the applied and restored file keeping its original permissions.
- Create-only controller skeleton proposal, apply, run record, and restore on the isolated fixture copy: the target must not exist, the action source stays byte-identical, and restore removes the generated file.
- Message bundle proposal, UTF-8 apply, and byte-exact restore on the isolated fixture copy, with unchanged keys and placeholders.
- JSP message/view-tag proposal, apply, taglib insertion, unused directive removal, legacy JSTL URI modernization, and byte-exact restore on the isolated fixture copy.
- `web.xml` Jakarta descriptor proposal, apply, namespace/schema/version verification, and byte-exact restore on the isolated fixture copy.
- Cross-file `struts-config.xml` message-resource proposal to a Spring Boot application config companion: the item source stays byte-identical, the YAML key appears, an externally modified target is rejected with `409`, an existing `spring.messages.basename` is rejected, a missing or ambiguous companion is rejected, the run records both hashes and a target backup outside the project, an externally modified source blocks restore, and restore returns both files to their originals.
- Same-file re-proposal after an applied transformation, with a manual edit still rejected, and batch preview/apply/restore on the isolated fixture copy, including failure rollback, batch audit events, and byte-exact restoration.
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
- Open every vetted-rule inspector, including the create-only controller skeleton with its new-file warning, the transaction rule's annotation swap, and the web descriptor's namespace change.
- Select two plan items, open the batch preview and confirmation modal, and verify the three-language layout and counts.
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
