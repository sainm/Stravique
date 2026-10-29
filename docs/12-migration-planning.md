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

`MESSAGE_RESOURCE` items carry the message bundle only. Its vetted transformation normalizes non-ASCII escapes to UTF-8; message APIs, lifecycle flow, and JSP error/message-block tags remain `MESSAGE_FLOW` or manual review work. `JSP_VIEW` items target `KEEP_JSP`; the vetted JSP rule rewrites self-closing `bean:message` tags as JSTL `fmt:message`, while form, error, and custom tags remain manual.

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
| `GET` | `/projects/{id}/migration-plans/{planId}/items` | Filtered, paged items |
| `GET` | `/projects/{id}/migration-plans/{planId}/items/{itemId}` | Item with evidence and dependency detail |
| `PATCH` | `/projects/{id}/migration-plans/{planId}/items/{itemId}` | Include or exclude an item |
| `GET` | `/projects/{id}/migration-plans/{planId}/graph` | Dependency graph from persisted dependencies |

Plan generation accepts `scanId` and `targetProfile`. With no `scanId`, the latest `COMPLETED` scan is used.

Graph limits:

- Up to 800 included items.
- Up to 3000 dependency edges whose endpoints are both returned.
- `itemCount` and `edgeCount` report true totals; `truncated`, `itemLimit`, and `edgeLimit` make the boundary explicit.

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
