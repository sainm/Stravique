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
- Opt-in scope carry-over on plan regeneration from any plan in the project: matching item keys keep their included state, new keys stay included, and unknown or cross-project sources are refused; a cross-scan source is validated against the new dependency graph and refused with `MIGRATION_PLAN_CARRY_OVER_SCOPE_CONFLICT` when an included item depends on a carried excluded prerequisite.
- Plan comparison export in CSV, localized Markdown, and JSON: only added, removed, and changed rows are written, CSV keeps stable English columns and a UTF-8 BOM, Markdown adds the plan/base summary, and unsupported formats are refused.
- Read-only scan snapshot comparison by relative path across any two completed scans: added, removed, changed, unchanged, and unknown counts, before/after type, size, encoding, status, and hash snapshots, a previous-completed default base, explicit base selection, and a 2000-row boundary with true summary counts.
- Scan-to-scan comparison in the Overview workspace with two selectors, five summary metrics, grouped rows, an explicit unknown-content state, and three-language labels; scan history also shows removed counts without mixing them into the current file total.
- Scan comparison export in CSV, Markdown, and JSON: stable English CSV columns with a UTF-8 BOM, localized Markdown with a summary and truncation notice, JSON isomorphic to the comparison response, three-language download links for the selected base, and an audit event per export; unsupported formats are refused.
- `JSP-BEAN-MESSAGE-JSTL` `1.3.0` converts contiguous literal message arguments (`arg0`..`arg3`) into nested `fmt:param` elements; argument gaps, EL/script values, unsafe text, and unknown attributes are refused.
- `JSP-BEAN-MESSAGE-JSTL` `1.4.0` maps literal `bean:write` scopes to EL scope objects and `filter="false"` to `escapeXml="false"`; unknown scopes, non-boolean filters, `ignore`, and `format` remain refused.
- `DICON-COMPONENT-SPRING-CONFIG` `1.6.0`: create-only Spring `@Configuration` generation for explicit dicon components, same-module relative include chains merged from the root dicon, anonymous components nested in properties and constructor arguments with deterministic bean names, reference properties/arguments, typed String/boolean/int/long/double literals, supported scopes, and existing no-arg init methods.
- Three-language controls for coverage, plan-run progress/cancel/resume/recover/restore, backup pruning, and plan comparison; the dicon inspector describes includes, nested components, naming, and refusal boundaries.
- Unit, integration, end-to-end, and browser visual checks for plan runs, plan run polling, backup ledgers, dicon rules, plan comparison, and scan snapshot comparison, including source-byte identity and isolate-fixture immutability.

### Changed

- `S2TX-SPRING-TRANSACTIONAL` `1.2.0` maps every propagation value shared with Spring (`REQUIRED`, `REQUIRES_NEW`, `SUPPORTS`, `NOT_SUPPORTED`, `MANDATORY`, `NEVER`).
- `WEBXML-JAKARTA-NAMESPACE` `1.2.0` also rewrites vetted `javax.*` class names in `servlet-class`, `filter-class`, `listener-class`, and `exception-type`, and can apply the class-name migration alone to a Jakarta EE 6.0 descriptor.
- `JAVAX-TO-JAKARTA` and the `web.xml` rule share one vetted `JakartaMappings` table so package and class-name migration cannot drift apart.
- Proposal preparation is reusable by a read-only probe, so coverage reports exactly whether the current rule can build a proposal without persisting one.
- The vetted rule catalog now publishes twelve rules, with current versions surfaced to the inspector and the browser checks; documentation, README, and delivery plan are updated through Phase 27.

### Fixed

- The file list's `structureRole` filter now parses the role like every other enum filter, so it is case-insensitive and trims, and an unknown role is refused with `INVALID_FILTER` instead of silently matching nothing.
- Scan comparison CSV cells use the same spreadsheet-formula guard as every other report export, so a path beginning with a formula trigger is prefixed rather than evaluated.
- Path overrides resolve to canonical paths: a symlink leaving the project (including one in a parent segment) and a missing directory are refused with `OVERRIDE_PATH_INVALID`, while a link staying inside is stored under its real path rather than the link name.
- Files marked `DELETED` no longer inflate scan file counts, overview totals, the inventory tree or file list, the `FILES` report (descriptor count and rows), or transformation companion reads. A scan reports the removed files separately, and inventory counts describe the files that exist in that snapshot.
