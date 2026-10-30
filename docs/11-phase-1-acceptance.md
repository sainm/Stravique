# Phase 1 Acceptance Record

## Automated acceptance

The committed verification command is:

```bash
./verify.sh
```

It covers the following acceptance areas.

| Requirement | Evidence |
| --- | --- |
| Import a Java web project | REST integration test registers the shipped multi-module fixture |
| Classify project files | Classifier tests plus full fixture scan |
| Detect module structure | Fixture test asserts at least three modules |
| Detect module dependencies | Fixture test asserts an evidence-linked Maven dependency from `web-app` to `domain` |
| Detect major frameworks | Fixture test asserts SAStruts evidence with `ANALYZED` rows |
| Save analysis evidence | Findings persist paths, lines, snippets, rule IDs, versions, and timestamps |
| Locate source positions | REST and UI evidence contain line ranges |
| Display uncertainty | Dynamic loading and reflection produce review-required findings |
| Display parse failures | Deliberately malformed XML produces a parse-failure record |
| Export reports | Report descriptors and CSV/JSON exports read persisted rows |
| Localize exports | Markdown exports assert per-locale titles and column labels while CSV and JSON field names stay stable |
| Verify the running application | End-to-end stage scans the fixture over HTTP and asserts the exported bytes, headers, and localized errors |
| Support three languages | Backend and frontend locale-key parity tests plus localized API error tests |
| Preserve source | Integration test compares SHA-256, size, and modification time before and after scanning |
| Modern developer UI | Project routes are rendered at desktop and mobile widths in three languages |
| Initial cross-file analysis | Java, Seasar2, configuration, Struts/SAStruts/JSP, DAO/SQL, and message relations are persisted with source evidence |
| Generate a migration plan | REST integration test generates a plan from a completed fixture scan |
| Link planning to evidence | Each item detail returns persisted finding records and dependency evidence |
| Derive dependencies from analysis | Graph edges come only from persisted analysis relations; the fixture produces action, component, data, message, view, configuration, and review items |
| Protect plan scope | Excluding an item still required by an included item returns `MIGRATION_DEPENDENCY_CONFLICT` |
| Expose planning status honestly | Plans emit only `READY`, `REVIEW_REQUIRED`, and `BLOCKED`; transformation statuses are not fabricated |
| Plan workspace | List, phase, dependency map, review, and history views render in three languages with persisted counts |
| Preview vetted transformations | Integration test generates Seasar DI → Spring DI, SAStruts `@Required` → `@NotNull`, ActionForm model, supported `javax.*` → `jakarta.*`, S2JDBC select and write chains → JdbcTemplate, create-only Spring controller skeleton, UTF-8 message-bundle, and JSTL message-tag diffs with persisted rule versions and hashes |
| Apply with protection | Edit apply requires confirmation, creates a backup, writes atomically, and updates a transformation run; create apply requires the target to be absent |
| Restore safely | Edit restore verifies the applied hash and backup hash before writing the original bytes; create restore deletes only the unchanged generated file |
| Create without overwriting | Controller skeleton apply leaves the legacy action byte-identical and refuses an existing target |
| Reject unsafe transformations | Unsupported items, stale plans, changed sources, and reused proposals are rejected |
| Protect shipped sources | Apply/restore tests run on an isolated copy; the shipped fixture bytes remain unchanged |

## Fixture expectations

The sample scan should report these minimum characteristics:

- At least 20 files.
- Three modules.
- At least ten technology keys.
- Both `SASTRUTS` and `STRUTS1` present.
- `SEASAR2` and `S2JDBC` present.
- One parse failure.
- At least two skipped generated/source-limit records.
- Review-required dynamic loading and reflection evidence.
- Struts tag-form actions and fields, Struts action mappings, forwards, and message resources.
- dicon component, injection, lifecycle, and scope evidence.

The fixture migration plan should report these minimum characteristics:

- At least ten items.
- Action-controller, component, data-access, message, JSP, configuration, and review item types.
- Both `READY` and `REVIEW_REQUIRED` items, plus one blocked parse-failure item.
- Dependency edges derived from imports, bindings, dicon injection, configuration references, message usage, SQL references, and JSP forms.
- More than one phase.
- No `TRANSFORMED`, `VALIDATED`, or `VALIDATION_FAILED` item.

Transformation acceptance uses a temporary copy of the fixture. It previews `OrderService.java` as a Seasar DI → Spring DI diff, `OrderAction.java` as a plain `@Required` → `@NotNull` diff, `OrderForm.java` as an ActionForm inheritance removal, `Order.java` as a supported `javax.*` → `jakarta.*` diff, `OrderQueryDao.java` as a read-only select diff, and `OrderDao.java` as a select-and-write diff whose parameter types map to `java.sql.Types`, applies each, verifies the backup and run records, restores the original bytes, and confirms the shipped fixture was never modified. It also previews `OrderAction.java` as a create-only controller skeleton, verifies the new file and `CREATE_FILE` run record, restores to remove the generated file, and rechecks the action source bytes. It previews `messages.properties` as a UTF-8 bundle, verifies the escaped Japanese message becomes literal UTF-8 text with unchanged keys, and restores the original bytes. It previews `edit.jsp` as a JSTL message-tag change, verifies the fmt taglib is added and the unused bean taglib removed, and restores the original bytes.

Exact counts can change when the fixture changes, but must remain derived from the current persisted scan.

## Browser verification

`npm run test:visual` uses the installed Chrome binary and checks:

- Project registry.
- Overview.
- Inventory Explorer with evidence inspector.
- Framework Usage.
- Relations table and map.
- Migration plan, review, and dependency map.
- Migration item inspector with transformation preview for edit rules, the create-only controller skeleton, message bundles, and JSP message tags.
- Reports.
- Settings.
- Desktop and mobile layouts.
- English, Simplified Chinese, and Japanese locales.
- Horizontal document overflow.
- Clipped primary buttons and mode controls.
- Browser console and page errors.

The command exits nonzero on any finding. Verification output belongs under `frontend/.artifacts/visual/` and is not product data.

## Manual review still required

Technical acceptance does not establish migration readiness. A human must still review:

- Dynamic class loading.
- Reflection.
- Custom framework conventions.
- Text-backed class references.
- JSP form and action candidates.
- Legacy encoding ambiguity.
- Runtime activation of any declared framework.
- Migration item risk, automation level, and scope decisions.
- Message lifecycle equivalence and data-access semantic equivalence.
- Transformation previews: annotation mapping, import side effects, dicon remains, and whether the resulting code compiles with the target stack.
- Apply/restore records and backup retention.

Phase 3 acceptance does not imply that any plan item has been transformed or validated. Phase 4 acceptance covers nine assisted rules—DI annotations, `@Required`, simple ActionForm inheritance, supported `javax.*` namespace imports, S2JDBC queries and parameterised writes, bare S2 transaction annotations, a create-only controller skeleton, UTF-8 message bundles, and JSTL message tags—and does not establish semantic equivalence, compilation, or runtime behavior.
