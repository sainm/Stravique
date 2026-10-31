# Changelog

All notable changes to Stravique are documented in this file.

## [Unreleased]

### Added

- Read-only plan coverage with per-item eligibility, rule/version, and an explicit ineligible reason; coverage never writes a file.
- Asynchronous plan-wide transformation runs: `202` start, persisted `RUNNING` history, phase-ordered processing in chunks of 50, optional targeted subsets, cooperative cancel with rollback, startup `INTERRUPTED` marking, explicit resume/recover, per-item results, and scoped restore.
- Flyway `V9`/`V10` migrations for persisted plan-run headers, children, and cancel state.
- Project backup ledger for edit runs with existence, size, run status, and restorable/prunable flags; `APPLIED` backups stay protected, and only `RESTORED` backups can be pruned with confirmation and audit.
- Read-only migration plan comparison by stable item key with added/removed/changed/unchanged counts and before/after values for scope, status, phase, risk, automation, title, and primary file path; the comparison refuses the same plan, a missing previous version, an unknown plan, and a cross-project base.
- Plan comparison in the Migration workspace with a previous-version default, an all-plan base selector, current-item navigation for added/changed rows, and an explicit absent marker for removed rows; all-plan base options are served separately from the 20-row history list.
- `DICON-COMPONENT-SPRING-CONFIG` `1.6.0`: create-only Spring `@Configuration` generation for explicit dicon components, same-module relative include chains merged from the root dicon, anonymous components nested in properties and constructor arguments with deterministic bean names, reference properties/arguments, typed String/boolean/int/long/double literals, supported scopes, and existing no-arg init methods.
- Three-language controls for coverage, plan-run progress/cancel/resume/recover/restore, backup pruning, and plan comparison; the dicon inspector describes includes, nested components, naming, and refusal boundaries.
- Unit, integration, end-to-end, and browser visual checks for plan runs, backup ledgers, dicon rules, and plan comparison, including source-byte identity and isolate-fixture immutability.

### Changed

- `S2TX-SPRING-TRANSACTIONAL` `1.2.0` maps every propagation value shared with Spring (`REQUIRED`, `REQUIRES_NEW`, `SUPPORTS`, `NOT_SUPPORTED`, `MANDATORY`, `NEVER`).
- `WEBXML-JAKARTA-NAMESPACE` `1.2.0` also rewrites vetted `javax.*` class names in `servlet-class`, `filter-class`, `listener-class`, and `exception-type`, and can apply the class-name migration alone to a Jakarta EE 6.0 descriptor.
- `JAVAX-TO-JAKARTA` and the `web.xml` rule share one vetted `JakartaMappings` table so package and class-name migration cannot drift apart.
- Proposal preparation is reusable by a read-only probe, so coverage reports exactly whether the current rule can build a proposal without persisting one.
- The vetted rule catalog now publishes twelve rules, with current versions surfaced to the inspector and the browser checks; documentation, README, and delivery plan are updated through Phase 20.
