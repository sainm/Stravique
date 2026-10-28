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
| Preview a vetted transformation | Integration test generates a Seasar DI → Spring DI unified diff with a persisted rule version and hash |
| Apply with protection | Apply requires confirmation, creates a backup, writes atomically, and updates a transformation run |
| Restore safely | Restore verifies the applied hash and backup hash before writing the original bytes |
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

Transformation acceptance uses a temporary copy of the fixture. It previews `OrderService.java` as a Seasar DI → Spring DI diff, applies it, verifies the backup and run record, restores the original bytes, and confirms the shipped fixture was never modified.

Exact counts can change when the fixture changes, but must remain derived from the current persisted scan.

## Browser verification

`npm run test:visual` uses the installed Chrome binary and checks:

- Project registry.
- Overview.
- Inventory Explorer with evidence inspector.
- Framework Usage.
- Relations table and map.
- Migration plan, review, and dependency map.
- Migration item inspector with transformation preview.
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

Phase 3 acceptance does not imply that any plan item has been transformed or validated. Phase 4A acceptance covers one assisted Seasar2 DI annotation rule only; it does not establish semantic equivalence, compilation, or runtime behavior.
