# Phase 4A Transformation Preview and Safe Apply

## Purpose

Phase 4A introduces the first vetted transformation path: evidence-linked Seasar2 dependency-injection annotations to Spring DI annotations. It is deliberately narrow and safety-first.

The engine can:

1. Generate a persisted unified diff from a migration item.
2. Validate the transformed Java source syntactically.
3. Apply the proposal only with explicit confirmation, a backup, and hash guards.
4. Restore the original bytes from the backup when the applied file is unchanged.
5. Retain proposal and run records for audit.

It cannot yet transform Struts actions, S2JDBC data access, messages, validation, XML, dicon, or JSP. Those items remain planning/review tasks; the interface says so instead of fabricating a transformation.

## Vetted rule

| Property | Value |
| --- | --- |
| Rule ID | `SEASAR-DI-SPRING-DI` |
| Rule version | `1.0.0` |
| Source item type | `COMPONENT_BEAN` |
| Target | `SPRING_DI` |
| Language | Java |

The rule uses the JavaParser AST to locate imports and annotations and replaces only those source ranges. It does not pretty-print or rewrite unrelated code.

Supported edits:

- `org.seasar.framework.container.annotation.tiger.Component` or `.twig.Component` import → `org.springframework.stereotype.Component`.
- `@Component` and `@Component(instance = "singleton")` → `@Component`.
- `@Component(name = "...")` → `@Component("...")`.
- `org.seasar.framework.container.annotation.tiger.Binding` or `.twig.Binding` import → `org.springframework.beans.factory.annotation.Autowired`.
- Plain `@Binding` → `@Autowired`.

Rejected for manual review:

- Other `@Component` attributes or non-singleton scopes.
- `@Binding` attributes.
- Ambiguous or fully qualified annotation use without the expected import.
- Files that cannot be parsed or whose ranges cannot be edited safely.

Import order is preserved at the modified line; if the target import already exists, the legacy import is removed instead of duplicated.

The rule changes DI annotations only. A component defined solely in dicon may receive only its annotation edits; the dicon definition remains a separate configuration item. A component file that also uses S2JDBC retains that data-access work as its own item.

## Safety model

Proposal generation:

- Requires a completed, current plan (a newer completed scan makes the plan stale).
- Requires the current source SHA-256 to equal the scan record's hash.
- Reads the file, builds the diff in memory, and writes only database rows.
- Supersedes earlier proposed versions for the same item.

Apply:

- Requires `confirmed: true`.
- Requires the proposal to still be `PROPOSED`.
- Rechecks the plan is current and the current file hash equals the proposal's original hash.
- Re-runs the same rule version and verifies the proposed hash equals the persisted proposal.
- Copies the original bytes to a backup directory outside the project before writing.
- Writes the proposed bytes atomically through a temporary file.
- Restores the original bytes if the database transaction fails.
- Never deletes or replaces the whole project.

Restore:

- Requires `confirmed: true`.
- Requires an `APPLIED` run and the current file hash equal to the applied hash.
- Verifies the backup bytes against the original hash.
- Writes only that file, atomically.
- Leaves the backup in place for audit.

Backup directory:

- `STRAVIQUE_TRANSFORMATION_BACKUP_DIR`.
- Default: `~/.stravique/transformation-backups`.
- A backup directory inside the source project is rejected.
- Backup paths are stored on the run record but are not exposed by the API.

## Validation boundary

The only automated validation is Java syntax parsing of the proposed source. This is not semantic equivalence, compilation against the project's dependencies, or runtime verification. Every generated proposal remains an assisted change requiring review before its migration item can be considered complete.

## Data model

- `transformation_proposal`: source file, item, rule/version, original and proposed hashes, unified diff, syntax-validation result, status, timestamps.
- `transformation_run`: applied/restored log with the proposal, item, file path, backup path, original/applied hashes, rule/version, timestamps.

Proposal statuses: `PROPOSED`, `APPLIED`, `RESTORED`, `SUPERSEDED`.

Run statuses: `APPLIED`, `RESTORED`, `FAILED`.

Migration item planning statuses remain unchanged by a transformation run.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/projects/{id}/migration-plans/{planId}/items/{itemId}/transformation-proposals` | Proposal history |
| `POST` | `/projects/{id}/migration-plans/{planId}/items/{itemId}/transformation-proposals` | Generate a read-only preview |
| `GET` | `/projects/{id}/migration-plans/{planId}/items/{itemId}/transformation-runs` | Execution history |
| `POST` | `/projects/{id}/transformation-proposals/{proposalId}/apply` | Apply with `{ "confirmed": true }` |
| `POST` | `/projects/{id}/transformation-runs/{runId}/restore` | Restore with `{ "confirmed": true }` |

Failure codes include unsupported item, stale plan, changed source, missing confirmation, already applied/superseded proposal, changed rule, missing proposal/run, and unsafe backup path.

## Interface

The migration item inspector shows the transformation area only where a vetted rule exists. It presents:

- The rule ID and version.
- The persisted diff.
- The syntax-validation result.
- An explicit source-write warning and confirmation checkbox.
- Apply and restore controls.
- Execution history.

Other migration items display that no vetted rule exists yet.

## OpenRewrite evaluation

OpenRewrite was evaluated and not adopted for this first slice:

- Its Java recipes are useful for greenfield Spring upgrades, but the shipped recipe ecosystem does not cover SAStruts, Seasar2, S2JDBC, dicon, or the message lifecycle.
- The legacy source targets old Java versions and custom frameworks; an AST-preserving JavaParser edit is smaller and keeps unrelated formatting untouched.
- A full OpenRewrite integration remains an adapter option for later rules when recipe-level semantics and test coverage justify it.

## Phase 4B boundary

Not implemented:

- Struts/SAStruts to Spring MVC transformation.
- S2JDBC to Spring JDBC transformation.
- Message/validation lifecycle transformation.
- XML, dicon, JSP, or TLD transformation.
- Compile or runtime validation.
- Batch execution across a plan.

Those require separate vetted rules with apply conditions, tests, and validation.
