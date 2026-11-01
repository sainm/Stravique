# Phase 3 Migration Planning

## Purpose

Phase 3 turns a completed inventory scan into an ordered, evidence-linked migration plan. It does not modify source files, generate patches, or claim that any migration has been performed.

The planning layer answers four questions:

1. Which analysis objects should become migration work?
2. Which work must happen before other work?
3. Which items require human review before automated work may begin?
4. Which evidence supports each decision?

A plan is derived only from persisted scan rows. It never re-parses the source tree and never invents a relationship to make the plan look complete.

## Scope

Implemented:

- Source and target profiles recorded on each plan.
- Migration item generation from framework, JSP, configuration, data, message, and review evidence.
- Automation level, risk, priority, and planning status per item.
- Dependencies derived from persisted cross-file relations.
- Dependency-aware phases.
- Evidence links from each item to findings and relations.
- Manual review queue for uncertain or failed analysis.
- Scope adjustment by excluding items that no included item depends on.
- Immutable plan versions with per-project sequence.
- Read-only comparison of two plan versions by stable item key.
- Plan list, list/phases/dependency graph/review views.

Not implemented:

- Source transformation.
- Patch generation or overwrite.
- Runtime validation.
- Migration item statuses beyond planning. `TRANSFORMED`, `VALIDATED`, and `VALIDATION_FAILED` remain reserved for later phases.

## Target profile

The first vetted target is `SPRING_BOOT_MVC`:

- Controllers: Spring MVC.
- Dependency injection: Spring container.
- Data access: Spring JDBC.
- Messages: Spring `MessageSource`/binding mechanisms.
- Views: JSP retained. Tag and form changes are planned separately; the product does not force a template-engine conversion.

Unsupported target profiles are rejected rather than silently mapped.

`sourceProfile` is derived from persisted technology keys and stored for audit. It is informational; rules use the actual findings, not the profile string.

## Planning rules

Every item carries a deterministic `itemKey` and links to its primary file and primary finding where one exists. Deleted file snapshots and skipped files do not produce migration items; a parse failure produces a blocked review item instead.

| Type | Evidence trigger | Target | Automation | Base risk | Base status |
| --- | --- | --- | --- | --- | --- |
| `ACTION_CONTROLLER` | SAStruts/Struts action, execute, forward, or validation findings | `SPRING_MVC` | `ASSISTED` | `MEDIUM` | `READY` |
| `FORM_MODEL` | SAStruts form binding or Struts ActionForm findings | `SPRING_MVC` | `ASSISTED` | `LOW` | `READY` |
| `COMPONENT_BEAN` | Seasar2 component/binding findings or dicon component definitions | `SPRING_DI` | `ASSISTED` | `MEDIUM` | `READY` |
| `DATA_ACCESS` | S2JDBC entity, DAO, query, transaction, or SQL statement evidence | `SPRING_JDBC` | `MANUAL` | `HIGH` | `REVIEW_REQUIRED` |
| `MESSAGE_FLOW` | Struts/SAStruts message lifecycle findings | `SPRING_MVC` | `ASSISTED` | `HIGH` | `REVIEW_REQUIRED` |
| `MESSAGE_RESOURCE` | Properties files referenced by message production or display relations | `SPRING_MESSAGES` | `ASSISTED` | `LOW` | `READY` |
| `VALIDATION` | SAStruts validation annotation findings | `SPRING_VALIDATION` | `ASSISTED` | `MEDIUM` | `READY` |
| `JAKARTA_NAMESPACE` | Supported `javax.*` imports | `JAKARTA_EE` | `ASSISTED` | `MEDIUM` | `READY` |
| `JSP_VIEW` | JSP page findings | `KEEP_JSP` | `ASSISTED` | `LOW` | `READY` |
| `CONFIGURATION` | web.xml, struts-config, or dicon findings | `SPRING_BOOT_CONFIG` | `ASSISTED` | `MEDIUM` | `READY` |
| `REVIEW` | Parse failure, dynamic loading, reflection, or project-owned framework evidence without another item | `MANUAL_REVIEW` | `MANUAL` | `HIGH` | `REVIEW_REQUIRED` |

`MESSAGE_RESOURCE` items carry the message bundle only. Its vetted transformation normalizes non-ASCII escapes to UTF-8; message APIs, lifecycle flow, and JSP error/message-block tags remain `MESSAGE_FLOW` or manual review work. `JSP_VIEW` items target `KEEP_JSP`; the vetted JSP rule rewrites self-closing `bean:message` as JSTL `fmt:message` with contiguous literal arguments as nested `fmt:param`, `bean:write` as `c:out` with literal scope and filter mapping, and modernizes legacy JSTL taglib URIs to `jakarta.tags.*`, while form, error, and custom tags remain manual. `CONFIGURATION` items cover several descriptor kinds. `web.xml` has a vetted rule that updates the Jakarta EE namespace, schema, and version and migrates vetted `javax.*` class names in servlet, filter, listener, and exception-type class elements. `struts-config.xml` has a vetted rule that adds `spring.messages.basename` to the module's Spring application config without changing the descriptor. Dicon explicit components can generate a create-only Spring `@Configuration`, including same-module relative include chains merged from the root dicon, anonymous nested property and constructor-argument components, simple reference properties mapped to public fields/setters, reference constructor arguments validated against the unique public constructor, typed String/boolean/numeric literals, supported scopes, and existing no-arg init methods; mismatched/escaped/out-of-range values, named/scoped nested components, malformed nested arguments, include cycles/missing/cross-module includes, AOP, richer dicon conversion, other Struts configuration elements, and full Spring Boot configuration generation remain manual. `VALIDATION` items are covered by one rule for plain `@Required` and integer length annotations; other SAStruts validation annotations remain manual.

Escalations:

- Dynamic loading, reflection, or project-owned framework evidence raises the owning item to `HIGH` risk and `REVIEW_REQUIRED`.
- Dicon aspects or interceptors raise the configuration item to `HIGH` risk and `REVIEW_REQUIRED`.
- Custom JSP tags raise a view item to `MEDIUM` risk and `REVIEW_REQUIRED`.
- A parse failure creates a `BLOCKED` review item; the plan remains usable but explicitly counts the blocker.
- Unsupported or missing evidence never upgrades a status. Dependencies do not upgrade certainty.

Priority is an attention order, not a schedule:

- `1`: blocked or review-required with high risk.
- `2`: ready with high risk, or review-required with medium risk.
- `3`: everything else.

## Dependencies

Dependencies are created only from persisted `analysis_relation` rows whose source and target files map to different plan items.

Supported relation types:

- `JAVA_IMPORT`
- `JAVA_INHERITANCE`
- `SEASAR_BINDING`
- `SEASAR_DICON_INJECTION`
- `CONFIG_CLASS_REFERENCE`
- `DAO_SQL_REFERENCE`
- `MESSAGE_PRODUCTION`
- `MESSAGE_DISPLAY`
- `JSP_FORM_TO_ACTION`

Direction: the source item depends on the target item. A message source file resolves to its message-flow item when one exists; a DAO SQL reference resolves from the data item.

JSP-to-Action and Action-to-JSP relations are not both turned into dependencies, because doing so would manufacture a cycle from navigation evidence. `JSP_FORM_TO_ACTION` yields a view item depending on the controller; action-to-JSP relations remain scan evidence only.

A dependency stores the originating relation ID, relation type, evidence path, line, and certainty. If either endpoint has no corresponding item, no dependency is created.

## Phases

Phases are computed with a topological pass over included items:

- Items with no included dependencies are in phase 1.
- Each dependent item is placed after its deepest prerequisite.
- A cycle is not hidden. Affected items are placed in a final phase, marked `REVIEW_REQUIRED`, and receive reason `DEPENDENCY_CYCLE`.

Phases describe dependency order only. They are not dates, effort estimates, or runtime promises.

## Plan lifecycle

```text
Generate plan from one COMPLETED scan
  -> plan version N (immutable analysis content)
  -> optional scope exclusion on items
  -> regenerate creates plan version N+1
```

A new scan does not rewrite an existing plan. The API compares the plan's scan with the latest completed scan and reports `stale`; the interface offers regeneration.

Excluding an item is rejected with `MIGRATION_DEPENDENCY_CONFLICT` when another included item depends on it. The caller must exclude dependents first. Re-including an item is rejected with `MIGRATION_PREREQUISITE_EXCLUDED` while one of its prerequisites is excluded. These guards keep the included subgraph closed. Scope changes are audited.

## API

Base path: `/api/v1`.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects/{id}/migration-plans` | Plan history, newest first, with staleness |
| `POST` | `/projects/{id}/migration-plans` | Generate a plan from a completed scan |
| `GET` | `/projects/{id}/migration-plans/{planId}` | Plan summary and phase counts |
| `GET` | `/projects/{id}/migration-plans/{planId}/export?format=...` | Complete plan export |
| `GET` | `/projects/{id}/migration-plans/{planId}/items` | Filtered, paged items |
| `GET` | `/projects/{id}/migration-plans/{planId}/items/{itemId}` | Item with evidence and dependency detail |
| `PATCH` | `/projects/{id}/migration-plans/{planId}/items/{itemId}` | Include or exclude an item |
| `GET` | `/projects/{id}/migration-plans/{planId}/graph` | Dependency graph from persisted dependencies |

Plan generation accepts `scanId` and `targetProfile`. With no `scanId`, the latest `COMPLETED` scan is used.

Graph limits:

- Up to 800 included items.
- Up to 3000 dependency edges whose endpoints are both returned.
- `itemCount` and `edgeCount` report true totals; `truncated`, `itemLimit`, and `edgeLimit` make the boundary explicit.

## Plan comparison

`GET /projects/{id}/migration-plans/{planId}/comparison` compares the selected plan with its previous version by default and accepts an explicit `basePlanId`. Items are matched by stable `itemKey`; the response counts added, removed, changed, and unchanged items and lists only rows that changed or appeared/disappeared. A changed row reports before/after values for included scope, status, phase, risk, automation, title, and primary file path. Rows are ordered added, removed, changed, then by title and key.

The comparison is read-only. A missing previous version, the same plan as base, an unknown base, and a base plan from another project are refused instead of inferring a comparison. The Migration dialog defaults to the previous sequence but lets the user choose any other project plan as the base; changed or added rows open the current plan's item inspector, and removed rows are marked as not present in the current plan.

The comparison can be downloaded as CSV, Markdown, or JSON. All formats contain only added, removed, and changed rows; CSV keeps stable English columns and a UTF-8 BOM, while Markdown and JSON also carry the plan/base identities and summary. Markdown localizes its title, summary, and headers, and invalid formats are refused.

## Complete plan export

The complete plan report contains all persisted items, phase counts, and dependency evidence; it is not limited to a comparison delta.

- JSON returns the full `MigrationPlanExportDto`.
- CSV contains one row per item with stable English columns, primary file and finding identifiers, item status/risk/automation/scope, and dependency evidence text.
- CSV starts with a UTF-8 BOM.
- Markdown localizes its title, metadata, summary, phase, item, and dependency headings and columns through `lang` / `Accept-Language`.
- The report is read-only and records `MIGRATION_PLAN_EXPORTED` in the audit log.
- Unsupported formats return `400 MIGRATION_PLAN_EXPORT_FORMAT_INVALID`.

## Scope carry-over on regeneration

Regenerating a plan can opt in to carrying the inclusion choices from any plan in the project. Items matched by `itemKey` keep their included or excluded state; keys that only exist in the new plan stay included. A source plan from another project is not found. Because a different scan can change the dependency graph, the carried scope is validated against the new graph: if an included item depends on an excluded prerequisite, generation is refused with `MIGRATION_PLAN_CARRY_OVER_SCOPE_CONFLICT` and no plan is persisted. The generate dialog exposes this as an off-by-default checkbox, lists every plan with its plan and scan sequence, and marks the cross-scan validation in its hint.

## Status discipline

Only these item statuses are emitted in Phase 3:

- `READY`: evidence is sufficient for planning; transformation is still not executed.
- `REVIEW_REQUIRED`: the item can be planned, but a human decision is required before transformation.
- `BLOCKED`: the item cannot be planned reliably (for example, its file failed to parse).

The remaining statuses are reserved for later phases:

- `TRANSFORMED`
- `VALIDATED`
- `VALIDATION_FAILED`

## UI

Route: `/projects/:projectId/migration`.

Views:

- Plan list with filters and scope controls.
- Phase lanes showing dependency order.
- Dependency map rendered from persisted edges, with truncation messaging.
- Review queue for `REVIEW_REQUIRED` and `BLOCKED` items.
- Plan history with staleness.
- Item inspector with source and dependency evidence.

All labels are localized. Item titles, file paths, rule IDs, statuses, relation types, and technology keys remain language-neutral technical values.

## Phase 4 boundary

Phase 4 may add transformation previews, recipe execution, and validation. Phase 3 provides the ordered, traceable plan those features must consume; it must not present a planned item as transformed. The first vetted preview/apply slice is documented in [13-transformation-preview.md](13-transformation-preview.md).
