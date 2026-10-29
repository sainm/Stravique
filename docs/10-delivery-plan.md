# Phase 1 Delivery Plan

## 1A Product foundation

- Monorepo and build scripts.
- Spring Boot application and PostgreSQL/Flyway schema.
- React shell, routes, design tokens, and localization.
- Project CRUD, runtime policy, and audit foundation.

Gate: project can be created, corrected, listed, and loaded without fabricated scan data.

## 1B Scan engine

- Allowlisted read-only discovery.
- Classification, module/root detection, hash and change tracking.
- Scan persistence, asynchronous states, cancellation, parse errors.
- Repeated-scan smoke fixture.

Gate: a sample source tree scans repeatedly and source hashes remain unchanged.

## 1C Framework inventory

- Java AST analysis.
- Maven, Gradle, XML, dicon, properties, JSP, TLD, SQL rules.
- SAStruts/Struts separation.
- Seasar2 and S2JDBC evidence.
- Technology aggregation and review findings.

Gate: shipped fixture yields line-linked evidence for each required target technology.

## 1D Workspace

- Overview.
- Projects.
- Inventory Explorer.
- Framework Usage.
- Reports.
- Project settings and path overrides.

Gate: all server counts come from persisted scan rows and all empty/error states are explicit.

## 1E Acceptance

- English, Chinese, and Japanese resource audit.
- REST and parser tests.
- Production frontend build.
- End-to-end sample scan and export.
- Source immutability verification.
- Documentation of local run, allowed roots, cancellation, limitations, and next phase boundary.

Gate: the acceptance checklist in `README.md` is evidenced by passing commands and runtime results.

## Planned Phase 2

Phase 2 now adds evidence-backed Java, Seasar2, Struts/SAStruts/JSP, DAO/SQL, configuration, and message relations. Remaining Phase 2 work:

- Runtime resolution of reflection and dynamic loading candidates.

No migration engine work begins until those relationships retain explicit provenance and uncertainty.

## Phase 3 Migration planning

- Source and target profiles.
- Migration item generation from persisted findings and relations.
- Evidence, risk, automation, priority, and planning status per item.
- Dependencies from persisted relations, with cycle surfacing.
- Dependency phases.
- Manual review queue and plan blockers.
- Scope exclusion with dependency protection.
- Immutable plan versions and staleness.
- List, phase, dependency map, review, and history workspace in three languages.

Gate: a completed fixture scan yields an ordered plan whose item counts, evidence links, and dependency edges match persisted rows; excluding a prerequisite is rejected; no source file changes.

## Phase 4 Transformation previews

- Nine vetted rules: seven Java rules—Seasar2 component/binding annotations to Spring DI, plain SAStruts `@Required` to Bean Validation `@NotNull`, simple Struts ActionForm inheritance removal, supported `javax.*` to Jakarta imports, a read-only S2JDBC query chain to Spring JdbcTemplate, a bare Seasar2 `@TransactionAttribute` to Spring `@Transactional`, and a create-only Spring controller skeleton from explicit Struts mappings—plus a message-bundle rule that rewrites non-ASCII escapes as UTF-8 and a JSP rule that rewrites self-closing `bean:message` tags as JSTL `fmt:message`.
- AST-range edits that preserve unrelated source formatting, a create-only artifact that never overwrites an existing file, properties edits that preserve keys, placeholders, comments, and continuations, and JSP tag edits that preserve tag structure.
- Persisted unified diff, rule version, hashes, and syntax validation.
- Guarded single-file apply with explicit confirmation, external backup for edits, no-overwrite creation, hash rechecks, and best-effort transaction rollback.
- Guarded restore from a verified backup for edits, or deletion of the unchanged generated file for creates.
- Proposal and execution history in the migration item inspector.
- Three-language interface and explicit unsupported-rule states.

Gate: previews are read-only; apply and restore are tested only on a copied fixture; the shipped fixture is never modified; a changed source, stale plan, or existing create target is rejected; no other transformation domain is presented as supported.

Remaining beyond Phase 4.

The first constraint on any new rule is the item slot, not the source construct. A plan item carries one item type and one target, and the engine selects exactly one rule for it, so two rules cannot both serve `DATA_ACCESS`: the second would silently lose to the one with the lower rule identifier. A domain therefore becomes addable in one of three ways: it fits an item type that has no rule yet (`MESSAGE_FLOW`, `CONFIGURATION`), it joins an existing rule as an extra form of the same concern, or it needs a new item type in the planner first.

| Domain | Evidence available today | Verdict |
| --- | --- | --- |
| S2JDBC write chains | `JAVA-S2JDBC-QUERY-001` fires on select, insert, update, and delete without recording which | Joins the read-only rule as an extra form, but the finding has to record the operation kind first, and the resulting item must not stay `READY` |
| S2 transaction annotations | `JAVA-S2JDBC-TX-001` exists, and the fixture now carries a sample | Done: `TRANSACTION` items plus `S2TX-SPRING-TRANSACTIONAL`, which accepts only the bare annotation and leaves transaction-manager configuration in review |
| Message APIs (`MessageResources#getMessage`) | `JAVA-STR1-MESSAGE-001` records message lifecycle calls only | Not mechanical. The Spring equivalent needs a `MessageSource` dependency the class does not have and a locale source the action does not carry; on an action that the controller skeleton is meant to replace, the rewrite is churn on code that will be deleted |
| Message lifecycle (`saveMessages`, `addErrors`) | Same finding | No local equivalent; it needs a `BindingResult` that does not exist yet |
| JSP error and message blocks | `JSP-MESSAGE-DISPLAY-001`, `JSP-STRUTS-MESSAGE-DISPLAY-001` | `<form:errors>` only resolves inside a `<form:form>` element with a model attribute, so a tag-level rewrite produces a page that fails at runtime |
| JSP form conversion | `JSP-FORM-ACTION-001`, `JSP-FORM-FIELD-001` | Needs the model attribute name and binding path, which static analysis cannot supply without inventing them |
| dicon to Spring configuration | `DICON-COMPONENT-001`, `DICON-PROPERTY-001`, `DICON-LIFECYCLE-001` | Cross-file artifact and a mapping that is not unique; `CONFIGURATION` is the free slot but the output would be a guess |
| TLD custom tags | `TLD-TAG-001`, `JSP-CUSTOM-TAG-USE-001` | No general target equivalent; per-tag human work |
| Struts controller method bodies | `JAVA-SAST-EXECUTE-002` plus the generated skeleton | Needs a parameter and return-type mapping table first, otherwise the generated body is a guess |

Also remaining:

- Compile or runtime validation. It needs the target stack on the classpath and conflicts with the local read-only boundary; the syntax-level guarantee stays.
- Batch execution across a plan, which is a workflow concern rather than a rule concern.
- Reported friction in the current flow: after an apply, a second item on the same file cannot be proposed until the source is rescanned, and a rescan makes every plan for that project stale, so multi-item files cost a rescan plus a plan regeneration each.
- Graph database projections.
- AI interpretation presented only as evidence, never fact.
