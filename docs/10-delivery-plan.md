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

- Ten vetted rules: seven Java rules—Seasar2 component/binding annotations and supported scopes to Spring DI, plain SAStruts `@Required` and integer length annotations to Bean Validation `@NotNull`/`@Size`, simple Struts ActionForm inheritance removal, supported `javax.*` to Jakarta imports, an S2JDBC select chain and parameterised writes to Spring JdbcTemplate, a bare Seasar2 `@TransactionAttribute` or its REQUIRED/REQUIRES_NEW propagation to Spring `@Transactional`, and a create-only Spring controller skeleton from explicit Struts mappings—plus a message-bundle rule that rewrites non-ASCII escapes as UTF-8, a JSP rule that rewrites self-closing `bean:message` as `fmt:message`, `bean:write` as `c:out`, and legacy JSTL taglib URIs as `jakarta.tags.*`, and a `web.xml` rule that updates the Java EE descriptor namespace, schema, and version to Jakarta EE 6.0.
- AST-range edits that preserve unrelated source formatting, a create-only artifact that never overwrites an existing file, properties edits that preserve keys, placeholders, comments, and continuations, JSP tag edits that preserve tag structure, and XML edits that preserve every other descriptor element.
- Persisted unified diff, rule version, hashes, and syntax validation.
- Guarded single-file apply with explicit confirmation, external backup for edits, no-overwrite creation, hash rechecks, and best-effort transaction rollback.
- Guarded restore from a verified backup for edits, or deletion of the unchanged generated file for creates.
- Proposal and execution history in the migration item inspector.
- Three-language interface and explicit unsupported-rule states.

Gate: previews are read-only; apply and restore are tested only on a copied fixture; the shipped fixture is never modified; a changed source, stale plan, or existing create target is rejected; no other transformation domain is presented as supported.

Remaining beyond Phase 4.

The first constraint on any new rule is the item slot, not the source construct. A plan item carries one item type and one target, and the engine selects exactly one rule for it, so two rules cannot both serve `DATA_ACCESS`: the second would silently lose to the one with the lower rule identifier. A domain therefore becomes addable in one of four ways: it fits an item type that has no rule yet (`MESSAGE_FLOW`), it fits a file-specific slot within a shared item type (`CONFIGURATION` for `web.xml`), it joins an existing rule as an extra form of the same concern, or it needs a new item type in the planner first.

| Domain | Evidence available today | Verdict |
| --- | --- | --- |
| S2JDBC write chains | `JAVA-S2JDBC-QUERY-001` fires on select, insert, update, and delete | Done: the select rule also rewrites `insertBySql`, `updateBySql`, and `deleteBySql` when the bound parameters match the declared types, mapping each type to `java.sql.Types` so no binding hint is dropped; the table now covers byte/Byte, BigInteger, LocalDate, LocalDateTime, and LocalTime in addition to the earlier primitives, wrappers, BigDecimal, Timestamp, and byte[]; an unmapped type, an unqualified `Date`, or a count mismatch is refused, while fully qualified `java.sql.Date`/`java.util.Date` are mapped by full name |
| S2 transaction annotations | `JAVA-S2JDBC-TX-001` exists, and the fixture now carries a sample | Done: `TRANSACTION` items plus `S2TX-SPRING-TRANSACTIONAL`, which accepts only the bare annotation and leaves transaction-manager configuration in review |
| Message APIs (`MessageResources#getMessage`) | `JAVA-STR1-MESSAGE-001` records message lifecycle calls only | Not mechanical. The Spring equivalent needs a `MessageSource` dependency the class does not have and a locale source the action does not carry; on an action that the controller skeleton is meant to replace, the rewrite is churn on code that will be deleted |
| Message lifecycle (`saveMessages`, `addErrors`) | Same finding | No local equivalent; it needs a `BindingResult` that does not exist yet |
| JSP error and message blocks | `JSP-MESSAGE-DISPLAY-001`, `JSP-STRUTS-MESSAGE-DISPLAY-001` | `<form:errors>` only resolves inside a `<form:form>` element with a model attribute, so a tag-level rewrite produces a page that fails at runtime |
| JSP form conversion | `JSP-FORM-ACTION-001`, `JSP-FORM-FIELD-001` | Needs the model attribute name and binding path, which static analysis cannot supply without inventing them |
| dicon to Spring configuration | `DICON-COMPONENT-001`, `DICON-PROPERTY-001`, `DICON-LIFECYCLE-001` | Cross-file artifact and a mapping that is not unique; `CONFIGURATION` is the free slot but the output would be a guess |
| Java EE web descriptor | `web.xml` file classification and configuration findings | Done: `WEBXML-JAKARTA-NAMESPACE` rewrites only the root namespace, schema location, and version to Jakarta EE 6.0; Spring Boot configuration conversion, filter/listener migration, and schema validation remain manual |
| TLD custom tags | `TLD-TAG-001`, `JSP-CUSTOM-TAG-USE-001` | No general target equivalent; per-tag human work |
| Struts controller method bodies | `JAVA-SAST-EXECUTE-002` plus the generated skeleton | Needs a parameter and return-type mapping table first, otherwise the generated body is a guess |

Also remaining:

- Compile or runtime validation. It needs the target stack on the classpath and conflicts with the local read-only boundary; the syntax-level guarantee stays.
- Batch execution across a plan is implemented in Phase 5 for selected items; execution across an entire plan in one action remains a future workflow.
- Graph database projections.
- AI interpretation presented only as evidence, never fact.

## Phase 5 Controlled multi-item workflow

- Same-file re-proposal after an applied vetted transform without a rescan, guarded by the latest applied hash.
- Batch preview, apply, and restore for up to 50 selected items/runs with explicit confirmation.
- Apply re-proposes each item in request order and restores already-applied items when a later item fails; rollback failure is explicit.
- Per-item audit plus batch audit events.
- Three-language selection bar, preview result, confirmation, and restore UI.
- Tests: same-file re-proposal, external-change rejection, batch apply/restore, failure rollback, and byte-exact fixture restoration.

Gate: manual edits after an apply are still rejected; a failed apply does not leave the earlier items silently applied; batch preview never writes; all tests, browser checks, i18n, and docs stay green. The detailed contract is in [14-multi-item-workflow.md](14-multi-item-workflow.md).

## Phase 4 close-out audit

This table is the delivery checklist for the remaining domains. A domain is either **Done** with a vetted rule/version, or **Boundary** with the evidence that is missing and the condition that would justify a future rule. A boundary is not presented as supported in the product.

| Domain | Current evidence / rule | Status | Missing evidence or safe mapping |
| --- | --- | --- | --- |
| DI annotations and scopes | `JAVA-SEASAR-*`; `SEASAR-DI-SPRING-DI` v1.1.0 | Done | Singleton plus prototype/request/session/application; `@Binding` attributes remain boundary |
| Form models | `FORM_BINDING`; `STRUTS-ACTIONFORM-MODEL` v1.0.0 | Done | Plain ActionForm inheritance only; lifecycle methods remain boundary |
| Validation | `JAVA-SAST-VALIDATION-001`; `SASTRUTS-VALIDATION-BEAN` v1.1.0 | Done | Plain `@Required` and integer length bounds; format/range/date/pattern annotations have different semantics |
| Jakarta namespace | `JAVA-*` imports; `JAVAX-TO-JAKARTA` v1.0.0 | Done | Supported import list; package expansion needs a target-dependency contract |
| S2JDBC data access | `JAVA-S2JDBC-QUERY-001`; `S2JDBC-QUERY-SPRING-JDBC` v1.3.0 | Done | Select and insert/update/delete; fully qualified Date types are mapped; Calendar/UUID and other types without an exact JDBC mapping remain boundary |
| S2 transaction | `JAVA-S2JDBC-TX-001`; `S2TX-SPRING-TRANSACTIONAL` v1.1.0 | Done | Bare plus REQUIRED/REQUIRES_NEW; other propagation values need an independently verified enum mapping |
| Action controllers | `STRUTS-CONFIG-ACTION-001`/`FORWARD-001`; `STRUTS-CONTROLLER-SKELETON` v1.0.0 | Done | Create-only skeleton; method bodies and request binding remain boundary |
| Message bundles | `MESSAGE_PRODUCTION`/`MESSAGE_DISPLAY`; `MESSAGE-RESOURCE-SPRING-UTF8` v1.0.0 | Done | UTF-8 escape normalization; message API/lifecycle remains boundary |
| JSP view tags | `JSP-*`; `JSP-BEAN-MESSAGE-JSTL` v1.2.0 | Done | Self-closing bean:message/bean:write and legacy JSTL URIs; form/error/custom tags remain boundary |
| Web descriptor | `web.xml` findings; `WEBXML-JAKARTA-NAMESPACE` v1.0.0 | Done | Namespace/schema/version only; Spring Boot config conversion and class-name migration remain boundary |
| Message APIs / lifecycle | `JAVA-STR1-MESSAGE-001`; `MESSAGE_FLOW` item | Boundary | Needs a `MessageSource` dependency, locale source, and a `BindingResult` that do not exist in the legacy action; not mechanical |
| JSP error and message blocks | `JSP-MESSAGE-DISPLAY-001`, `JSP-STRUTS-MESSAGE-DISPLAY-001` | Boundary | Requires a `<form:form>` model attribute and binding context; a tag-level rewrite can fail at runtime |
| JSP form conversion | `JSP-FORM-ACTION-001`, `JSP-FORM-FIELD-001` | Boundary | Needs an explicit Spring model-attribute and binding contract; the Struts form-bean name alone is not proof of the controller model name |
| dicon / other configuration | `DICON-*`, `STRUTS-CONFIG-*` | Boundary | Cross-file output and non-unique bean/property mappings; a guessed Spring configuration would fabricate semantics |
| Advanced validation | `JAVA-SAST-VALIDATION-001` | Boundary | Pattern/range/date validators do not share exact semantics with Jakarta constraints; require per-annotation evidence |
| TLD custom tags | `TLD-TAG-001`, `JSP-CUSTOM-TAG-USE-001` | Boundary | No general target equivalent; per-tag human work |
| Reflection and dynamic loading | review findings | Boundary | Runtime resolution is not established by static evidence |
| Compile/runtime validation | none | Boundary | Requires the target classpath and runtime; conflicts with the read-only local boundary |
| Batch execution and same-file multi-item apply | selected-item batch API/UI; Phase 5 same-file hash rule | Done | Whole-plan one-click execution remains a future workflow; selected-item batches are implemented |
| TLD/custom view and graph/AI projections | `TLD-TAG-001`, review items | Boundary | Out of Phase 4 rule scope; AI output remains evidence only |

The rule catalog and its versions are the executable part of this audit; the boundary rows are the reason the product shows "no vetted rule" instead of generating a guess.
