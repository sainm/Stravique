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
| Support three languages | Backend and frontend locale-key parity tests plus localized API error tests |
| Preserve source | Integration test compares SHA-256, size, and modification time before and after scanning |
| Modern developer UI | Six project routes are rendered at desktop and mobile widths in three languages |

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

Exact counts can change when the fixture changes, but must remain derived from the current persisted scan.

## Browser verification

`npm run test:visual` uses the installed Chrome binary and checks:

- Project registry.
- Overview.
- Inventory Explorer with evidence inspector.
- Framework Usage.
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
