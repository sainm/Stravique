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
