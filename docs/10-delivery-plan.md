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

- Ten vetted rules: seven Java rules—Seasar2 component/binding annotations and supported scopes to Spring DI, plain SAStruts `@Required` and integer length annotations to Bean Validation `@NotNull`/`@Size`, simple Struts ActionForm inheritance removal, supported `javax.*` to Jakarta imports, an S2JDBC select chain and parameterised writes to Spring JdbcTemplate, a bare Seasar2 `@TransactionAttribute` or the six propagation values shared with Spring to `@Transactional`, and a create-only Spring controller skeleton from explicit Struts mappings—plus a message-bundle rule that rewrites non-ASCII escapes as UTF-8, a JSP rule that rewrites self-closing `bean:message` as `fmt:message`, `bean:write` as `c:out`, and legacy JSTL taglib URIs as `jakarta.tags.*`, and a `web.xml` rule that updates the Java EE descriptor namespace, schema, and version to Jakarta EE 6.0.
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
| dicon to Spring configuration | `DICON-COMPONENT-001`, `DICON-PROPERTY-001`, `DICON-LIFECYCLE-001` | Done for the strict subset: `DICON-COMPONENT-SPRING-CONFIG` generates a create-only `@Configuration` for named components, same-module relative include chains, anonymous nested property and constructor-argument components, simple reference properties, reference constructor arguments, typed String/boolean/numeric literals, supported scopes, and existing no-arg init methods; named/scoped nested components, AOP, and richer dicon remain boundary |
| Java EE web descriptor | `web.xml` file classification and configuration findings | Done: `WEBXML-JAKARTA-NAMESPACE` v1.1.0 rewrites the root namespace, schema location, and version to Jakarta EE 6.0 and migrates vetted `javax.*` class names in servlet/listener/filter class elements; Spring Boot configuration conversion, complete filter/listener behavior migration, and schema validation remain manual |
| TLD custom tags | `TLD-TAG-001`, `JSP-CUSTOM-TAG-USE-001` | No general target equivalent; per-tag human work |
| Struts controller method bodies | `JAVA-SAST-EXECUTE-002` plus the generated skeleton | Needs a parameter and return-type mapping table first, otherwise the generated body is a guess |

Also remaining:

- Compile or runtime validation. It needs the target stack on the classpath and conflicts with the local read-only boundary; the syntax-level guarantee stays.
- Selected-item batch execution is implemented in Phase 5; Phase 7 adds a persisted, confirmed run over every executable plan item with preflight, rollback, history, and restore.
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

## Phase 6A Cross-file configuration editing

- A proposal and its run carry both the item source hash and the target file hash; apply and restore reject an externally changed source or target.
- The first cross-file rule, `STRUTS-CONFIG-MESSAGE-SPRING`, reads `<message-resources parameter>` from `struts-config.xml` and appends `spring.messages.basename` to the module's existing `application.yml`, `application.yaml`, or `application.properties`.
- The rule is additive: it never removes the Struts declaration and never writes the item source.
- Missing, duplicate, or ambiguous application config targets, an existing `spring.messages.basename`, and malformed source or target files are refused for manual review.
- Target bytes are backed up outside the project and restored from that backup; a create target remains no-overwrite.
- The inspector shows the target file chip and target-write warning instead of the source-write warning.
- Tests cover YAML and properties output, target external-change rejection, source byte identity, apply/restore of both files, and the catalog hint.

Gate: a target change between proposal and apply is rejected exactly like a source change; restore returns both files to their original bytes; the shipped fixture stays byte-identical. The detailed contract is in [15-cross-file-config.md](15-cross-file-config.md).

## Phase 6B Web descriptor class names

- `WEBXML-JAKARTA-NAMESPACE` moves to `1.1.0`.
- Besides the Java EE/J2EE namespace, schema, and version, the rule rewrites vetted `javax.*` class names inside `servlet-class`, `filter-class`, `listener-class`, and `exception-type` elements to `jakarta.*`.
- A Jakarta EE 6.0 descriptor can receive the class-name migration alone; a Java EE descriptor receives both changes in one proposal.
- An unmapped `javax` class name, content that is not a single class name, or malformed XML refuses the whole proposal; no partial class rewrite is emitted.
- Comments, surrounding whitespace, and every other descriptor element are preserved.
- Tests cover class-name migration in the combined and Jakarta-only paths, unknown-class rejection, comments, integration apply/restore, and the catalog version.

Gate: a descriptor containing an unmapped `javax` class is refused rather than partially migrated; an applied and restored descriptor returns to its exact proposal-time bytes; the shipped fixture stays byte-identical; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 6C Transaction propagation coverage

- `S2TX-SPRING-TRANSACTIONAL` moves to `1.2.0`.
- The rule maps every propagation value whose name and semantics are shared by Seasar2 and Spring: `REQUIRED` (default annotation), `REQUIRES_NEW`, `SUPPORTS`, `NOT_SUPPORTED`, `MANDATORY`, and `NEVER`.
- An unknown constant, a non-enum value, or an attribute other than `value` is still refused for manual review; no guessed enum mapping is emitted.
- Tests cover each shared value, REQUIRED collapsing to the default, import reuse, and unknown-value rejection.

Gate: every mapped value produces the same-named Spring `Propagation` constant; an unknown value fails the whole proposal; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 7 Plan-wide controlled run

- Read-only plan coverage reports every item as executable, rule-backed but currently ineligible, not ready, excluded, or without a rule, including the probe error code.
- Eligibility runs the same prepare path as a proposal but persists and writes nothing.
- A confirmed plan run applies every eligible item in phase/priority/title/id order, in internal chunks of 50 under one persisted plan-run header and one rollback scope.
- A later failure restores every item applied by that plan run in reverse order; rollback failures are explicit per item.
- History and detail survive reload; restore is plan-run scoped and refuses a run with nothing applied.
- The Migration plan view shows coverage, recent runs, confirmation, results, and restore in three languages.

Gate: preflight never writes; an item whose rule cannot transform the current file is excluded rather than failing the run; a failed run leaves no silently applied item from that run; restore returns files to their pre-run bytes or reports which changed; the shipped fixture is never modified; all checks stay green. The contract is in [16-plan-wide-run.md](16-plan-wide-run.md).

## Phase 8 Asynchronous and cancellable plan runs

- `POST` returns `202` with a persisted `RUNNING` plan run; execution continues on the application task executor.
- A second `RUNNING` run for the same plan is refused. Progress is read from the persisted header and child rows.
- A confirmed cancel sets a cooperative flag; the worker stops before the next item, restores applied children, and records `CANCELLED` or `ROLLBACK_FAILED`.
- Startup marks every leftover `RUNNING` run as `INTERRUPTED` with an audit event; recovery remains explicit and only covers children persisted as `APPLIED`.
- The result dialog polls progress, offers cancel while running, recovery while interrupted, and restore after success, in three languages.

Gate: start never blocks on item execution; a cancel never leaves untracked applied items; startup never leaves a false `RUNNING` record; recovery refuses non-interrupted runs; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 9 Resumable plan runs

- `POST .../{planRunId}/resume` returns `202` for an `INTERRUPTED` run whose children are `APPLIED` or `NOT_ATTEMPTED`.
- The worker keeps applied children, processes the first unattempted item onward, and uses the normal chunk, cancel, and hash guards.
- A failure or cancel during resume rolls back every applied child of the run, including those applied before the interruption.
- A successful resume returns the header to `APPLIED` with the full applied count and writes a resume audit event.
- Resume is explicit and never automatic; the unknown-write window from a crash remains stated.

Gate: resumed runs never re-propose already applied children; a resumed failure leaves no silently applied item from that run; a non-interrupted run cannot be resumed; all checks stay green.

## Phase 10 Targeted plan runs and coverage detail

- The coverage panel lists every plan item with its rule/version, ineligibility reason, current planning status, and applied state.
- Executable items are selectable; the view defaults to all eligible selected and supports select-all, clear, and show-all.
- The plan-run request accepts an optional `itemIds` subset; absent or empty means every eligible item. Every requested item must be currently eligible.
- Selected items execute in persisted dependency phase order; counts, cancel, resume, recover, and restore are scoped to the selected children.
- Non-eligible items are rejected with `TRANSFORMATION_PLAN_ITEMS_INVALID`; unselected eligible items are never written.

Gate: a selected run never writes an unselected file; an ineligible selection is refused; the browser shows rule/reason rows without overflow; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 11 Strict dicon configuration generation

- `DICON-COMPONENT-SPRING-CONFIG` supports direct named singleton components with an explicit project class; constructor arguments, includes, AOP, and nested components are refused.
- The rule validates the class is public and has an implicit or public no-arg constructor, chooses a common package, and creates a `@Configuration` file under the dicon module's `src/main/java` root.
- The dicon is never modified; apply is create-only and restore removes the generated file only when its bytes still match.
- Richer dicon properties, external classes, and other elements remain boundary.

Gate: only explicit simple components generate code; unsupported dicon files are refused rather than guessed; the generated file is syntactically valid; source bytes stay unchanged; coverage, targeted runs, and restore include the rule; all checks stay green.

## Phase 12 dicon property references and init methods

- `DICON-COMPONENT-SPRING-CONFIG` moves to `1.1.0`.
- A simple property reference generates a `@Qualifier`-annotated `@Bean` parameter and a field assignment or setter call; the target must be a public field or public setter with a resolvable type.
- An existing public no-arg `initMethod` is invoked after property assignment.
- Literal/quoted/numeric properties, missing fields/setters, conflicting reference types, nested components, constructor arguments, includes, AOP, non-singleton scope, and missing classes/init methods are refused.
- External referenced beans must exist at runtime; property wiring is not guessed.

Gate: only evidenced field/setter references and existing init methods generate code; unsupported property forms are refused; the generated file is syntactically valid; dicon bytes stay unchanged; coverage and restore include the rule; all checks stay green.

## Phase 13 dicon constructor arguments and scopes

- `DICON-COMPONENT-SPRING-CONFIG` moves to `1.2.0`.
- Simple component-reference `<arg>` values generate qualified `@Bean` parameters and are passed to the unique public constructor; constructor count and parameter count must match exactly.
- `prototype`, `request`, `session`, and `application` instances map to Spring `@Scope`; blank or `singleton` remains the default.
- Literal arguments, nested arguments, missing/ambiguous constructors, unknown scopes, and all previously refused dicon elements remain refused.
- Generated code is create-only; the dicon stays byte-identical; coverage, targeted runs, and restore include the rule.

Gate: constructor and scope mappings are evidence-backed; unsupported forms are refused; generated code parses; source bytes stay unchanged; all checks stay green.

## Phase 14 Transformation backup ledger and safe pruning

- A project ledger lists every edit run with a backup: existence, size, run status, and restorable/prunable flags.
- `APPLIED` backups are protected; only `RESTORED` run backups can be pruned.
- Prune requires confirmation, validates the external backup root, deletes only regular files, reports requested/deleted/failed counts, and writes an audit event.
- The Migration view shows backup metrics, recent rows, and a confirmation dialog in three languages.

Gate: an applied backup is never deleted; a restored backup is deleted only after confirmation; missing/unsafe paths are reported; all checks stay green.

## Phase 15 dicon typed literals

- `DICON-COMPONENT-SPRING-CONFIG` moves to `1.3.0`.
- Quoted String, boolean, integer, long, and decimal literals are emitted only when they match the target field, setter, or constructor parameter type.
- References and literals can be mixed in the same component; only references become qualified parameters.
- Mismatched, escaped, out-of-range, and unsupported values are refused.

Gate: generated literals match their target types exactly; unsupported values are refused; generated code parses; dicon bytes stay unchanged; all checks stay green.

## Phase 16 dicon include composition

- `DICON-COMPONENT-SPRING-CONFIG` moves to `1.4.0`.
- Same-module relative `<include path="..."/>` elements are followed and their explicit components merged in declaration order into one create-only `@Configuration` named after the root dicon.
- A dicon that is included by another project dicon is refused for direct migration; the root dicon owns the combined configuration.
- Include cycles, missing dicons, absolute or cross-module paths, duplicate component names across the chain, and include elements with extra attributes or child elements are refused; a diamond include is merged once.
- Existing property references, constructor arguments, typed literals, scopes, and init methods keep working across the merged component set.
- Coverage marks the included dicon ineligible with `TRANSFORMATION_UNSUPPORTED`; a targeted run that selects it is rejected with `TRANSFORMATION_PLAN_ITEMS_INVALID`; the root targeted run and restore operate on the generated file while every dicon stays byte-identical.

Gate: the root dicon produces one combined configuration; an included dicon cannot be migrated directly; a selected run never writes an included dicon; restore removes the generated configuration and leaves the chain byte-identical; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 17 dicon anonymous nested components

- `DICON-COMPONENT-SPRING-CONFIG` moves to `1.5.0`.
- A `<property>` may contain one anonymous `<component class="...">`; it is resolved as its own bean named `<parentBean><CapitalizedProperty>` and wired into the parent with a qualified parameter and a field/setter assignment.
- Nested components recursively support constructor arguments, simple properties, and one init method; the parent property type must match the nested class exactly.
- Named, scoped, or auto-bound nested components, nested constructor-argument components, type mismatches, duplicate bean names, and unknown nested elements are refused.
- Include chains, top-level scopes, references, literals, and init methods keep working; the configuration remains create-only and restore removes only the generated file.

Gate: anonymous nested components generate compilable separate beans in declaration order; unsupported nested forms are refused; dicon bytes stay unchanged; coverage, targeted runs, and restore include the rule; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 18 migration plan version comparison

- `GET /projects/{id}/migration-plans/{planId}/comparison` compares two plans of one project by stable `itemKey`, defaulting to the previous per-project sequence.
- The response reports added, removed, changed, and unchanged counts; changed rows carry before/after values for included scope, status, phase, risk, automation, title, and primary file path.
- A same-plan base, a missing previous version, an unknown base, and a base plan from another project are refused with distinct error codes.
- The Migration summary exposes a three-language comparison dialog with metric chips and grouped rows; the metrics wrap to two columns on narrow viewports.
- The comparison is read-only: no item, plan, or file is written, and unchanged rows are counted but not listed.

Gate: identical plans report every item unchanged; a scope change appears as an `INCLUDED` change; added and removed items appear after rescans; the same plan, missing previous version, unknown base, and cross-project base are refused; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 19 dicon anonymous nested constructor arguments

- `DICON-COMPONENT-SPRING-CONFIG` moves to `1.6.0`.
- An `<arg>` may contain one anonymous `<component class="...">`; it is resolved as its own bean named `<parentBean>Arg<1-based argument position>` and passed to the parent constructor through a qualified parameter.
- Nested argument components recursively support constructor arguments, simple properties, and one init method; the parent constructor parameter type must match the nested class exactly.
- Named, scoped, or auto-bound nested argument components, text plus a component, multiple or unknown child elements, type mismatches, duplicate bean names, and missing classes are refused.
- Include chains, property nesting, literal/reference arguments, top-level scopes, and init methods keep working; the configuration remains create-only and restore removes only the generated file.

Gate: anonymous constructor-argument components generate compilable separate beans in argument order; unsupported nested argument forms are refused; dicon bytes stay unchanged; coverage, targeted runs, and restore include the rule; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 20 plan comparison base selection and row actions

- The comparison dialog lists every other project plan as a selectable base and defaults to the previous sequence; changing the base refetches the pair.
- Comparison rows carry the current plan's `itemId` for added and changed items; the dialog opens the current plan's item inspector for those rows.
- Removed rows have no current item and are marked as not present in the current plan.
- The dialog remains read-only and keeps the three-language labels and narrow-viewport metric wrapping.

Gate: any other plan version can be selected and re-queries the comparison; a changed or added row opens the current item inspector; a removed row is explicitly marked absent; the same-plan, missing-previous, unknown-base, and cross-project guards still hold; all backend, frontend, end-to-end, visual, i18n, and documentation checks stay green.

## Phase 4 close-out audit

This table is the delivery checklist for the remaining domains. A domain is either **Done** with a vetted rule/version, or **Boundary** with the evidence that is missing and the condition that would justify a future rule. A boundary is not presented as supported in the product.

| Domain | Current evidence / rule | Status | Missing evidence or safe mapping |
| --- | --- | --- | --- |
| DI annotations and scopes | `JAVA-SEASAR-*`; `SEASAR-DI-SPRING-DI` v1.1.0 | Done | Singleton plus prototype/request/session/application; `@Binding` attributes remain boundary |
| Form models | `FORM_BINDING`; `STRUTS-ACTIONFORM-MODEL` v1.0.0 | Done | Plain ActionForm inheritance only; lifecycle methods remain boundary |
| Validation | `JAVA-SAST-VALIDATION-001`; `SASTRUTS-VALIDATION-BEAN` v1.1.0 | Done | Plain `@Required` and integer length bounds; format/range/date/pattern annotations have different semantics |
| Jakarta namespace | `JAVA-*` imports; `JAVAX-TO-JAKARTA` v1.0.0 | Done | Supported import list; package expansion needs a target-dependency contract |
| S2JDBC data access | `JAVA-S2JDBC-QUERY-001`; `S2JDBC-QUERY-SPRING-JDBC` v1.3.0 | Done | Select and insert/update/delete; fully qualified Date types are mapped; Calendar/UUID and other types without an exact JDBC mapping remain boundary |
| S2 transaction | `JAVA-S2JDBC-TX-001`; `S2TX-SPRING-TRANSACTIONAL` v1.2.0 | Done | Bare plus all six propagation values shared with Spring; unknown constants and values outside that shared set remain refused |
| Action controllers | `STRUTS-CONFIG-ACTION-001`/`FORWARD-001`; `STRUTS-CONTROLLER-SKELETON` v1.0.0 | Done | Create-only skeleton; method bodies and request binding remain boundary |
| Message bundles | `MESSAGE_PRODUCTION`/`MESSAGE_DISPLAY`; `MESSAGE-RESOURCE-SPRING-UTF8` v1.0.0 | Done | UTF-8 escape normalization; message API/lifecycle remains boundary |
| JSP view tags | `JSP-*`; `JSP-BEAN-MESSAGE-JSTL` v1.2.0 | Done | Self-closing bean:message/bean:write and legacy JSTL URIs; form/error/custom tags remain boundary |
| Web descriptor | `web.xml` findings; `WEBXML-JAKARTA-NAMESPACE` v1.2.0 | Done | Namespace/schema/version plus vetted servlet/filter/listener/exception-type class names; Spring Boot config conversion, complete filter/listener behavior migration, and descriptors other than `web.xml` remain boundary |
| Message APIs / lifecycle | `JAVA-STR1-MESSAGE-001`; `MESSAGE_FLOW` item | Boundary | Needs a `MessageSource` dependency, locale source, and a `BindingResult` that do not exist in the legacy action; not mechanical |
| JSP error and message blocks | `JSP-MESSAGE-DISPLAY-001`, `JSP-STRUTS-MESSAGE-DISPLAY-001` | Boundary | Requires a `<form:form>` model attribute and binding context; a tag-level rewrite can fail at runtime |
| JSP form conversion | `JSP-FORM-ACTION-001`, `JSP-FORM-FIELD-001` | Boundary | Needs an explicit Spring model-attribute and binding contract; the Struts form-bean name alone is not proof of the controller model name |
| dicon / other configuration | `DICON-*`, `STRUTS-CONFIG-*`; `STRUTS-CONFIG-MESSAGE-SPRING` v1.0.0; `DICON-COMPONENT-SPRING-CONFIG` v1.6.0 | Done for the strict subset | Message basename, explicit dicon components with same-module include chains and anonymous nested property/constructor-argument components, reference properties/constructor arguments, typed literals, supported scopes, and existing init methods are generated; named/scoped nested components, AOP/aspects, other Struts config elements, and full Spring Boot configuration remain non-unique and are refused |
| Advanced validation | `JAVA-SAST-VALIDATION-001` | Boundary | Pattern/range/date validators do not share exact semantics with Jakarta constraints; require per-annotation evidence |
| TLD custom tags | `TLD-TAG-001`, `JSP-CUSTOM-TAG-USE-001` | Boundary | No general target equivalent; per-tag human work |
| Reflection and dynamic loading | review findings | Boundary | Runtime resolution is not established by static evidence |
| Compile/runtime validation | none | Boundary | Requires the target classpath and runtime; conflicts with the read-only local boundary |
| Batch execution and same-file multi-item apply | selected-item batch API/UI; Phase 5 same-file hash rule; Phase 7/8 plan-wide preflight/run | Done | Selected batches and an asynchronous, cancellable plan-wide run are implemented; a plan run is bounded by the 800-item plan cap |
| TLD/custom view and graph/AI projections | `TLD-TAG-001`, review items | Boundary | Out of Phase 4 rule scope; AI output remains evidence only |

The rule catalog and its versions are the executable part of this audit; the boundary rows are the reason the product shows "no vetted rule" instead of generating a guess.
