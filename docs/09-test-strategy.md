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
- File hash diff classification, including an unreadable file that is recorded as skipped rather than failing the scan.
- Report serialization and CSV escaping, including a value that a spreadsheet would evaluate as a formula and a value that only contains the trigger character.
- Every report descriptor's declared row count equals the number of rows its JSON export returns, so a count query that drifts from its export fails the suite.
- Localized Markdown titles and column labels with stable CSV and JSON field names.
- Report message coverage derived from the report declarations, so a new type or column cannot ship without labels or leave orphaned keys.
- Technology certainty aggregation.
- Migration plan graph adaptation and prerequisite-before-dependent layout.
- Seasar2 DI → Spring DI AST-range rule: imports, component/binding annotations, prototype/request/session/application → `@Scope`, singleton omission, formatting preservation, and rejected constructs.
- SAStruts `@Required` → Bean Validation `@NotNull` and length annotations → `@Size`, including import merge/removal, rejected attribute forms, duplicate annotations, conflicting imports, and non-integer values.
- Struts ActionForm inheritance removal rule, including lifecycle-method and residual-reference rejection.
- Supported `javax.*` → `jakarta.*` import rule, including wildcard imports and unsupported-package refusal.
- S2JDBC `selectBySql` and insert/update/delete writes → JdbcTemplate rule, including the expanded `java.sql.Types` table, rejection of unmapped types and mismatched parameter counts, and unsupported manager usage.
- Seasar2 `@TransactionAttribute` → Spring `@Transactional` rule: bare and all six shared propagation values, REQUIRED collapsing to the default annotation, propagation import reuse, and rejection of unknown constants, non-enum values, duplicate `@Transactional`, a missing import, and an import with no annotation.
- Struts controller skeleton rule: explicit-mapping generation, package and forward filtering, no invented routes, and supported item/target.
- Message bundle UTF-8 rule: non-ASCII escape conversion, comment and continuation preservation, surrogate pairs, rejected malformed bundles, and unchanged message maps.
- JSP tag rule: `bean:message` conversion including contiguous literal arguments as nested `fmt:param` and custom fmt-prefix reuse, `bean:write` conversion including literal scope-to-EL mapping and `filter=false` to `escapeXml=false`, taglib reuse and insertion, unused taglib removal, legacy JSTL URI modernization, rejected attribute/body forms, argument gaps, unsafe argument text, unknown scopes or filters, and unknown URIs, and unbalanced tag structure rejection.
- `web.xml` Jakarta rule: Java EE and J2EE namespace/schema/version conversion, servlet/filter/listener/exception-type class-name migration, class-name-only migration on an already-Jakarta descriptor, unknown `javax` class rejection, commented elements staying untouched, unsupported-version rejection, malformed XML, and file-name support.
- dicon configuration rule: explicit components generate a create-only `@Configuration`; same-module relative include chains are merged in declaration order, only the root dicon is migrated, and an included dicon is refused; one anonymous component nested in a property becomes a separate bean named after the parent and property and is wired into the parent, and one nested in an argument becomes a separate bean named after the parent and argument position and is passed to the constructor; simple reference properties generate `@Qualifier` parameters and field/setter assignments, reference constructor arguments use the unique public constructor, typed String/boolean/int/long/double literals are emitted for matching targets, supported scopes generate `@Scope`, existing init methods are invoked, and mismatched/escaped/out-of-range values, missing targets, ambiguous constructors, named/scoped/auto-bound nested components, malformed nested arguments, include cycles/missing/cross-module/invalid includes and duplicate component names, unknown scopes/attributes, missing classes, non-public classes, and unusable constructors are refused; target path and class checks are covered.
- Atomic source replacement: a replaced file keeps its own permissions and executable bit, a created file takes the platform default rather than the private mode of a staging file, a shorter replacement truncates, and no staging file survives.
- Paged query ordering ends at the row identifier and never repeats a column, so offset paging cannot duplicate or drop rows that share a sort key.
- Migration plan comparison calculator: added/removed/changed/unchanged counts, added-removed-changed row order with title/key ties, every compared field in stable order with nullable primary paths, and no rows for identical plans.
- Override path policy: a plain directory resolves to its canonical path; links escaping the project directly or through a parent segment, missing directories, and parent traversal are refused with `OVERRIDE_PATH_INVALID`; a link staying inside is canonicalized.
- Scan comparison export CSV: formula-trigger paths are prefixed, quotes and newlines stay inside one cell, and ordinary paths are unchanged; the exporter shares the report CSV guard instead of keeping a second escaping implementation.
- Scan comparison calculator: added/removed/changed/unchanged/unknown counts, a size difference counting as changed when a hash is unavailable, equal unavailable hashes staying unknown rather than unchanged, unchanged rows staying out of the listed rows, and status-then-path ordering.

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
- `web.xml` Jakarta descriptor proposal, apply, namespace/schema/version and `javax.servlet` class-name verification, and byte-exact restore on the isolated fixture copy.
- dicon configuration proposal, coverage eligibility, create-only apply, generated `@Configuration` verification (including a `@Qualifier` reference assignment), dicon byte identity, and restore removal on the isolated fixture copy; a dicon with a nested component is reported ineligible.
- dicon include composition on the isolated fixture copy: a root dicon that includes a same-module dicon produces one combined configuration, the included dicon is ineligible with `TRANSFORMATION_UNSUPPORTED` and rejected for a targeted run with `TRANSFORMATION_PLAN_ITEMS_INVALID`, the root targeted run applies and restores the generated file, and both dicons stay byte-identical.
- dicon anonymous nested components on the isolated fixture copy: a nested property component and a nested constructor-argument component become separate generated beans with their init methods, the parent receives them through qualified references, coverage reports the dicon eligible, a targeted run applies and restores the configuration, and the dicon stays byte-identical.
- Cross-file `struts-config.xml` message-resource proposal to a Spring Boot application config companion: the item source stays byte-identical, the YAML key appears, an externally modified target is rejected with `409`, an existing `spring.messages.basename` is rejected, a missing or ambiguous companion is rejected, the run records both hashes and a target backup outside the project, an externally modified source blocks restore, and restore returns both files to their originals.
- Same-file re-proposal after an applied transformation, with a manual edit still rejected, and batch preview/apply/restore on the isolated fixture copy, including failure rollback, batch audit events, and byte-exact restoration.
- Plan coverage on the isolated fixture copy: supported/unsupported counts, an item with a vetted rule but no current transformation is reported as ineligible with `TRANSFORMATION_UNSUPPORTED`, and no file is written during preflight.
- Plan run on the isolated fixture copy: every eligible item is proposed and applied in phase order, file changes are visible, history/detail are persisted, restore returns every captured file byte-exactly, and a second restore is refused.
- Plan-run rollback: a later item's write is blocked, earlier items are applied and then restored, the failing item is recorded, and no applied child remains for restore.
- Plan-run recovery: an applied run is forced to `RUNNING`, the startup marker changes it to `INTERRUPTED`, recovery restores the tracked children to their original bytes, the header becomes `RECOVERED`, the audit event is written, and a second recovery is refused.
- Plan-run cancellation: an orphaned `RUNNING` header with applied children is cancelled, the children are rolled back to their original bytes, the header becomes `CANCELLED`, and a second cancel is refused.
- Plan-run resume: an applied child is restored and marked unattempted, the header is set to `INTERRUPTED`, resume returns `202`, skips applied children, reapplies the remaining item, returns to `APPLIED`, writes the resume audit event, and refuses a second resume.
- Plan-run start returns `202` and `RUNNING`; integration tests poll the detail endpoint to the terminal state before asserting file bytes.
- Backup ledger: one applied and one restored edit produce ledger rows; confirmation is required; pruning removes the restored backup, keeps the applied backup, reports counts, writes the audit event, and leaves no prunable backup.
- The end-to-end stage asserts an empty backup ledger before any transformation is applied.
- A targeted plan run accepts two eligible items, leaves a third eligible file byte-identical, persists only two child rows, restores them, and rejects a non-eligible item with `TRANSFORMATION_PLAN_ITEMS_INVALID`.
- The shipped fixture remains byte-identical after transformation tests.
- Plan comparison: regenerating a plan after excluding one item reports exactly one `INCLUDED` change with the current item ID and the rest unchanged; adding and removing a dicon before rescans produces added rows with item IDs and removed rows without one; the default base is the previous sequence; same-plan, missing-previous, unknown, and cross-project bases are refused.
- Plan scope carry-over: regenerating with a same-scan source keeps the excluded leaf excluded and reports no comparison change against that source, while regeneration without carry-over includes it again; a cross-scan source is accepted when the new graph stays closed; unknown and other-project sources are not found; a newly introduced binding that makes an included item depend on the carried excluded item is refused with `MIGRATION_PLAN_CARRY_OVER_SCOPE_CONFLICT`.
- Plan comparison export: CSV starts with a UTF-8 BOM and stable English columns and contains the scope change; Markdown localizes the Chinese title, plan lines, summary, and headers; JSON matches the comparison plan, summary, and current item ID; an unsupported format returns `MIGRATION_PLAN_COMPARISON_FORMAT_INVALID`.
- Scan comparison: two completed scans of a copied fixture compare with exactly one added, one changed, and two removed files; the default base is the previous completed scan; same-scan, unknown, other-project, and non-completed scans are refused with their distinct codes; rows list added before removed before changed with before/after snapshots and no unchanged rows.
- Path overrides: a symlink pointing outside the project is refused with `OVERRIDE_PATH_INVALID` without wiping the stored overrides, while a link staying inside is saved under its real path.
- Scan comparison export: CSV starts with a UTF-8 BOM and stable quoted English columns and contains added, removed, and changed rows; Markdown localizes the Chinese title, scan lines, summary, and headers; JSON matches the comparison scan, base, summary, and row count; an unsupported format returns `SCAN_COMPARISON_FORMAT_INVALID`, a self base returns `SCAN_COMPARISON_SELF`, and three successful downloads write three audit events.
- File list filters: `structureRole` matches case-insensitively and returns exactly the rows an uppercase role returns, while an unknown role is refused with `INVALID_FILTER`.
- Removed files are excluded from scan counts, overview totals, the paged file list, and the `FILES` report descriptor and rows, while the removed count reports them separately.

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
- Plan comparison grouping order, field value localization, and base-version options/defaulting.
- Scan comparison export URLs carry the base scan, format, and active interface language, and omit the language when none is active.
- Scan comparison grouping order, completed-scan defaults, previous-completed base defaulting, hash shortening, and a rendered dialog that defaults to the latest completed scan, renders all five metrics and groups, refetches on current-scan change, and states when fewer than two completed scans exist.
- Report URL generation.
- Migration page batch selection: excluding an item drops it from the batch and refetches the plan-wide coverage, using a rendered page with a stubbed API rather than isolated helpers.
- Plan run polling: a rendered page starts a plan-wide run, fails the run status poll, and shows that failure instead of leaving the progress count frozen with no way out.
- Navigation and responsive class contracts.

## Visual verification

When a browser runtime is available:

- Render the project routes, including migration plan, review, and dependency map, at 1440 px and 390 px.
- Inspect English, Chinese, and Japanese navigation, tables, buttons, and error wrapping.
- Check evidence panes and status labels for overflow.
- Check relation filters and source/target rows for overflow.
- Check migration item rows, plan metrics, and the migration dependency canvas for content and overflow.
- Open every vetted-rule inspector, including the create-only controller skeleton with its new-file warning, the transaction rule's annotation swap, and the web descriptor's namespace and class-name changes.
- Capture the plan coverage panel and the plan-run confirmation in every language and viewport.
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
- Confirms complete migration-plan JSON/CSV/Markdown exports and localized Markdown content.
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
