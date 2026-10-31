# Phase 4 Transformation Preview and Safe Apply

## Purpose

Phase 4 introduces narrow, vetted transformation paths: evidence-linked Seasar2 dependency-injection annotations to Spring DI, plain SAStruts validation annotations to Jakarta Bean Validation, simple Struts form models to plain Spring models, supported `javax.*` imports to Jakarta, an S2JDBC select chain and parameterised writes to Spring JDBC, a create-only Spring controller skeleton from explicit Struts action mappings, UTF-8 normalization of Struts message bundles, JSTL message tags for self-closing `bean:message` usage, a Jakarta EE `web.xml` descriptor, and an additive Struts message-resources config bridge. It is deliberately narrow and safety-first.

The engine can:

1. Generate a persisted unified diff from a migration item.
2. Validate the transformed artifact with a language-appropriate parser.
3. Apply the proposal only with explicit confirmation, a backup for edits or a no-overwrite check for creates, and hash guards.
4. Restore the original bytes from the backup when the applied file is unchanged.
5. Create a new file without overwriting an existing one, and remove that generated file on restore when it is unchanged.
6. Retain proposal and run records for audit.

It cannot yet migrate Struts controller method bodies, request binding, message APIs, message lifecycle, JSP form/error/custom tags, S2JDBC writes whose parameter types have no vetted SQL mapping, Spring Boot configuration conversion, dicon, other XML descriptors, or JSP forms. Validation support is limited to plain `@Required` and the integer length annotations; other validation annotations remain manual. Unsupported items remain planning/review tasks; the interface says so instead of fabricating a transformation.

## Vetted rules

| Rule ID | Source item type | Target | Language |
| --- | --- | --- | --- |
| `SEASAR-DI-SPRING-DI` | `COMPONENT_BEAN` | `SPRING_DI` | Java |
| `SASTRUTS-VALIDATION-BEAN` | `VALIDATION` | `SPRING_VALIDATION` | Java |
| `STRUTS-ACTIONFORM-MODEL` | `FORM_MODEL` | `SPRING_MVC` | Java |
| `JAVAX-TO-JAKARTA` | `JAKARTA_NAMESPACE` | `JAKARTA_EE` | Java |
| `S2JDBC-QUERY-SPRING-JDBC` | `DATA_ACCESS` | `SPRING_JDBC` | Java |
| `S2TX-SPRING-TRANSACTIONAL` | `TRANSACTION` | `SPRING_JDBC` | Java |
| `STRUTS-CONTROLLER-SKELETON` | `ACTION_CONTROLLER` | `SPRING_MVC` | Java |
| `MESSAGE-RESOURCE-SPRING-UTF8` | `MESSAGE_RESOURCE` | `SPRING_MESSAGES` | Properties |
| `JSP-BEAN-MESSAGE-JSTL` | `JSP_VIEW` | `KEEP_JSP` | JSP |
| `WEBXML-JAKARTA-NAMESPACE` | `CONFIGURATION` | `SPRING_BOOT_CONFIG` | XML |
| `STRUTS-CONFIG-MESSAGE-SPRING` | `CONFIGURATION` | `SPRING_BOOT_CONFIG` | YAML |

The annotation and import rules use the JavaParser AST to locate imports and annotations and replace only those source ranges. The data-access rule rewrites a select chain to `query` with a `BeanPropertyRowMapper`, and a write chain to `update` with the declared parameter types carried across as `java.sql.Types` values, because dropping them would leave a null parameter with no bindable type; a type outside the vetted table is refused rather than guessed. The transaction rule accepts a bare `@TransactionAttribute` and the explicitly vetted `REQUIRED`/`REQUIRES_NEW` propagation values; other propagation values are refused rather than mapped onto a name that may not mean the same thing. The controller skeleton rule parses the action source and generates a separate file. The message rule parses Java properties and rewrites only non-ASCII escapes in message values. The JSP rule parses tags and directives, rewrites supported message/view tags, and modernizes legacy JSTL taglib URIs. The web descriptor rule rewrites only the `web-app` namespace, schema location, and version on `web.xml`. The config bridge rule reads `struts-config.xml` and appends one Spring message key to the module application config; it is the only rule whose target differs from the item source, protected by both source and target hashes. No rule pretty-prints or rewrites unrelated code.

### Seasar2 DI → Spring DI

Supported edits:

- `org.seasar.framework.container.annotation.tiger.Component` or `.twig.Component` import → `org.springframework.stereotype.Component`.
- `@Component` and `@Component(instance = "singleton")` → `@Component`.
- `@Component(instance = "prototype|request|session|application")` → `@Component @Scope("…")`, with the Spring `Scope` import added or reused.
- `@Component(name = "...")` → `@Component("...")`.
- `org.seasar.framework.container.annotation.tiger.Binding` or `.twig.Binding` import → `org.springframework.beans.factory.annotation.Autowired`.
- Plain `@Binding` → `@Autowired`.

Rejected for manual review:

- Other `@Component` attributes or an unsupported `instance` value.
- An existing `@Scope` on the component, or a conflicting `Scope` import.
- `@Binding` attributes.
- Ambiguous or fully qualified annotation use without the expected import.
- Files that cannot be parsed or whose ranges cannot be edited safely.

Import order is preserved at the modified line; if the target import already exists, the legacy import is removed instead of duplicated.

The rule changes DI annotations only. A component defined solely in dicon may receive only its annotation edits; the dicon definition remains a separate configuration item. A component file that also uses S2JDBC retains that data-access work as its own item.

### SAStruts validation → Bean Validation

Supported edits:

- `org.seasar.struts.annotation.Required` import → `jakarta.validation.constraints.NotNull`.
- Plain `@Required` → `@NotNull`.
- `@Length(min = …, max = …)` → `@Size(min = …, max = …)`.
- `@MaxLength(n)` → `@Size(max = n)`; `@MinLength(n)` → `@Size(min = n)`.
- The source imports are replaced by the required Jakarta imports, or removed when the target import already exists.

Rejected for manual review:

- `@Required` with attributes.
- Length annotations with message or other attributes, non-decimal values, negative values, `min > max`, or more than one length annotation on the same declaration.
- A length annotation next to an existing `@Size`, or `@Required` next to an existing `@NotNull`.
- A conflicting `NotNull` or `Size` import from another namespace.
- Other SAStruts validation annotations such as ranges, patterns, and dates.
- Files without a supported annotation.

This rule transforms validation annotations only. A controller that also uses `@Execute`, message lifecycle APIs, or a custom action base type retains those items. It does not move validation into a Spring `BindingResult` flow; that remains a message/controller migration item.

### Struts ActionForm → Spring model

Supported edits:

- `org.apache.struts.action.ActionForm` import removal.
- `extends ActionForm` removal from a plain model class.

Rejected for manual review:

- Classes with `reset` or `validate` lifecycle methods.
- Any remaining `ActionForm` type reference after the edit.
- Classes with additional superclass structure.
- Files without the expected import or superclass.

This turns a simple Struts form bean into a plain model class. It does not convert controller binding, validation messages, or JSP form tags.

### javax namespace → Jakarta namespace

Supported imports:

- `javax.servlet`, `javax.annotation`, `javax.persistence`, `javax.validation`, `javax.inject`, `javax.transaction`, `javax.xml.bind`, `javax.ws.rs`, `javax.mail`, and `javax.activation`.

Only import names change; annotations, types, and wildcards remain structurally intact. Other `javax.*` packages are left untouched. This rule is intended for a Spring Boot 3 target; dependency and runtime verification remain manual.

### S2JDBC query and write → Spring JdbcTemplate

Supported edits:

- One `JdbcManager` field → `JdbcTemplate`.
- `selectBySql(Entity.class, "SQL", args...).getResultList()` → `query("SQL", new BeanPropertyRowMapper<>(Entity.class), args...)`.
- `insertBySql`/`updateBySql`/`deleteBySql(...).execute(args...)` → `update("SQL", new Object[]{args...}, new int[]{Types...})`.
- Parameter types are carried across as `java.sql.Types`; the vetted table includes String, int/Integer, long/Long, short/Short, byte/Byte, boolean/Boolean, double/Double, float/Float, BigDecimal, BigInteger, Timestamp, java.sql.Date, java.sql.Time, java.util.Date, LocalDate, LocalDateTime, LocalTime, and byte[]. Fully qualified names win over simple names, so `java.sql.Date` maps to DATE and `java.util.Date` maps to TIMESTAMP.
- Adds `JdbcTemplate`, `BeanPropertyRowMapper`, and `java.sql.Types` imports as needed.

Rejected for manual review:

- Other `JdbcManager` calls, initialized or multiple manager fields, and non-literal SQL.
- Non-class-literal parameter types, unmapped types, an unqualified `Date`, or a parameter count that does not match the declared types.
- Transaction, pagination, and SQL semantic concerns beyond syntax.

The rule intentionally leaves Seasar DI and transaction annotations for their separate rules. Applying it to a class with remaining Seasar injection or transaction configuration still requires those migrations; the proposal states the data-access-only scope.

### Seasar2 transaction → Spring @Transactional

Supported edits:

- A bare `@TransactionAttribute` → `@Transactional`.
- `@TransactionAttribute(TransactionAttributeType.REQUIRED)` → `@Transactional`.
- `@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)` → `@Transactional(propagation = Propagation.REQUIRES_NEW)`, with the Spring `Propagation` import added or reused.

Rejected for manual review:

- Other propagation values.
- Non-enum values or attributes other than `value`.
- A Seasar annotation next to an existing `@Transactional`, or a conflicting `Transactional`/`Propagation` import.
- Transaction-manager configuration.

### Struts message bundle → UTF-8 Spring messages

Supported edits:

- Non-ASCII `\uXXXX` escapes in message values → literal Unicode characters.
- The file is written as UTF-8, matching the Spring `MessageSource` default encoding.
- Keys, placeholders, separators, comments, line continuations, and already-literal text are preserved.

Rejected for manual review:

- Files without non-ASCII escapes in message values.
- Malformed properties files or escapes.
- Any conversion that would change the decoded key/value map.
- Escapes in keys or comments, ASCII-only escapes, and escaped values that cannot be represented safely as UTF-8.

This rule changes bundle encoding only. Configuring `spring.messages.basename`, migrating `MessageResources`/`ActionMessage` APIs, `BindingResult` flow, and JSP error/message-block tags remain separate items. Message values with `{0}` placeholders keep the same placeholder text; Spring must still be configured to use `MessageFormat` where needed.

### Struts view tags and legacy JSTL → Jakarta JSTL

Supported edits:

- Self-closing `bean:message` tags with only `key` and optional `bundle` attributes → `fmt:message`.
- Self-closing `bean:write` tags with a literal `name` and optional literal `property` path → `c:out value="${name.property}"`.
- Legacy `http://java.sun.com/jsp/jstl/core|fmt|functions|sql|xml` taglib URIs → `jakarta.tags.*`.
- The fmt and core taglibs are reused when present; otherwise `jakarta.tags.fmt`/`jakarta.tags.core` directives are added after the page directive.
- An unused bean taglib directive is removed when no other bean tags remain.
- Tag structure is validated before proposal and before apply; unbalanced JSP tag structures are rejected.

Rejected for manual review:

- `bean:message` bodies, `arg0`-style parameters, and attributes other than `key`/`bundle`.
- `bean:write` bodies or attributes such as `scope`, `filter`, `format`, or `ignore`; non-literal names or property paths.
- Pages without a Struts bean/view tag or a supported legacy JSTL taglib.
- A legacy JSTL URI outside the vetted core/fmt/functions/sql/xml set.
- A page where the required `fmt` or `c` prefix is bound to another taglib.
- Unbalanced prefixed-tag structures.

This rule changes message/view tags and legacy JSTL taglib URIs only. Struts form, error, and custom tags, `html:messages` blocks, model attributes, and controller binding remain manual. A page that conditionally balances tags through scriptlets is rejected rather than guessed at.

### Struts action → Spring controller skeleton (create-only)

Supported output:

- A new `<ActionClass>Controller.java` in the action's package.
- `@Controller` and one `@RequestMapping` method per explicit Struts action mapping that has a forward.
- Method names prefer an `@Execute` name that matches the action path; otherwise they derive from the path.
- The return value strips `/WEB-INF/`, a leading `jsp/`, and the `.jsp` suffix.

Rejected for manual review:

- Actions without an explicit mapping in the scanned Struts configuration.
- Mappings without a non-redirect forward; the rule does not invent a view name.
- Routes whose configured type belongs to a different package.

The artifact type is `CREATE_FILE`, not `EDIT_FILE`. Routes come from persisted `STRUTS-CONFIG-ACTION-001` and non-redirect `STRUTS-CONFIG-FORWARD-001` findings in the plan's scan, so rescan after configuration changes. The legacy action is never modified, method bodies and request binding are not migrated, and dependencies or annotations beyond the skeleton are not inferred. Apply refuses if the target already exists; restore deletes the generated file only when its hash still matches the applied hash. A created proposal has no backup path.

### Java EE web.xml → Jakarta EE 6.0 descriptor

Supported edits:

- A Java EE or J2EE `web-app` namespace → `https://jakarta.ee/xml/ns/jakartaee`.
- The matching `web-app_2_3` … `web-app_4_0` schema file → `web-app_6_0.xsd`.
- The root `version` attribute → `6.0`.
- All other descriptor content is preserved.

Rejected for manual review:

- Files not named `web.xml`, non-`web-app` roots, already-Jakarta descriptors, unsupported versions, or single-quoted version attributes.
- Malformed XML.
- Spring Boot configuration conversion, filter/listener class migration, and schema validation remain separate manual work.

The rule is matched by item type, target, and the `web.xml` file-name hint in the rule catalog. It makes no claim that the descriptor is valid against the Jakarta schema; only XML well-formedness and the resulting root namespace/version are checked.

### Struts message-resources → Spring messages config

Supported edits:

- Read the explicit `<message-resources parameter="...">` value from `struts-config.xml`.
- Append `spring.messages.basename: "<value>"` to the module's `application.yml`/`.yaml`, or `spring.messages.basename=<value>` to `application.properties`.
- Preserve the target's existing content and line endings.
- Leave `struts-config.xml` byte-identical.

Rejected for manual review:

- No message-resources declaration, a blank parameter, no application config target, or more than one target.
- An existing `spring.messages.basename` key.
- Malformed XML or an unsupported target type.
- Removing the old declaration, dicon conversion, filter/listener migration, and full Spring Boot configuration conversion.

The proposal stores both the source hash and the target hash; apply requires both to still match, backs up and writes the target, and restore returns the target from that backup. The detailed contract is in [15-cross-file-config.md](15-cross-file-config.md).

## Safety model

Proposal generation:

- Requires a completed, current plan (a newer completed scan makes the plan stale).
- Requires the current source SHA-256 to equal the scan record's hash, or the `appliedSha256` of the latest `APPLIED` run for the same project and path. Any other change is rejected; see [14-multi-item-workflow.md](14-multi-item-workflow.md).
- For a cross-file edit, records the target file's current hash as `targetSha256`; the source file is never the target.
- Reads the file, builds the diff in memory, and writes only database rows.
- Refuses a create proposal when the target path already exists or its parent is outside the project.
- Supersedes earlier proposed versions for the same item.

Apply:

- Requires `confirmed: true`.
- Requires the proposal to still be `PROPOSED`.
- Rechecks the plan is current, the current source hash equals the proposal's original hash, and the target hash equals `targetSha256` (for same-file rules both hashes are the same file).
- Re-runs the same rule version and verifies the proposed hash equals the persisted proposal.
- Uses the rule's declared output encoding when hashing and writing; the message rule writes UTF-8.
- For an edit, backs up the target bytes outside the project before writing, and restores the target bytes if the database transaction fails.
- For a create, verifies the target does not exist before writing, and deletes the created file if the database transaction fails.
- Writes atomically through a temporary file.
- Never deletes or replaces the whole project.

Restore:

- Requires `confirmed: true`.
- Requires an `APPLIED` run and the current file hash equal to the applied hash.
- For an edit, verifies the backup bytes against the target hash and writes only that target file, atomically.
- For a create, deletes only that generated file.
- Leaves an existing backup in place for audit.

Backup directory:

- `STRAVIQUE_TRANSFORMATION_BACKUP_DIR`.
- Default: `~/.stravique/transformation-backups`.
- A backup directory inside the source project is rejected.
- Backup paths are stored on the run record but are not exposed by the API.

## Validation boundary

The only automated validation is syntax parsing of the proposed artifact: Java parsing for Java rules, properties parsing plus UTF-8 round-tripping for the message rule, prefixed-tag structure validation for the JSP rule, and secure XML well-formedness plus root namespace/version checks for the web descriptor rule. This is not semantic equivalence, compilation against the project's dependencies, full JSP compilation, schema validation, or runtime verification. For the message rule, the decoded key/value map is also compared before a proposal is returned; for the JSP rule, only tag renames, legacy JSTL URI updates, and directive changes are made, and conditional scriptlet structure is rejected. Every generated proposal remains an assisted change requiring review before its migration item can be considered complete.

## Data model

- `transformation_proposal`: source file, item, rule/version, artifact type, source and target hashes, target path, unified diff, syntax-validation result, status, timestamps.
- `transformation_run`: applied/restored log with the proposal, item, artifact type, file path, backup path, source/target/applied hashes, rule/version, timestamps.

Artifact types: `EDIT_FILE` and `CREATE_FILE`. A rule may declare a non-source output encoding; the message rule declares UTF-8. The encoding is re-derived from the rule on apply and is not stored separately.

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
| `GET` | `/transformation-rules` | Vetted rule catalog, including language and optional file-name hint |

Failure codes include unsupported item, stale plan, changed source, missing confirmation, already applied/superseded proposal, changed rule, missing proposal/run, unsafe backup path, existing create target, and invalid target path. A create proposal returns no backup path, and its run record stores a null backup path.

## Interface

The interface reads the vetted rule catalog from `GET /transformation-rules` and enables the transformation area only when a rule matches the item type, target, and optional file-name hint. The migration item inspector shows the transformation area only where a vetted rule exists. It presents:

- The rule ID and version.
- The persisted diff.
- The syntax-validation result.
- A new-file marker and create-specific warning and confirmation for `CREATE_FILE` proposals.
- An explicit source-write or file-create warning and confirmation checkbox.
- Apply/create and restore/remove controls.
- Execution history.

Other migration items display that no vetted rule exists yet.

## OpenRewrite evaluation

OpenRewrite was evaluated and not adopted for this first slice:

- Its Java recipes are useful for greenfield Spring upgrades, but the shipped recipe ecosystem does not cover SAStruts, Seasar2, S2JDBC, dicon, or the message lifecycle.
- The legacy source targets old Java versions and custom frameworks; an AST-preserving JavaParser edit is smaller and keeps unrelated formatting untouched.
- A full OpenRewrite integration remains an adapter option for later rules when recipe-level semantics and test coverage justify it.

## Phase 4 boundary

Not implemented:

- Struts controller method bodies, request binding, form handling, and JSP form conversion.
- Message APIs, message lifecycle, `BindingResult` flow, and JSP error/message-block tags.
- S2JDBC writes whose parameter types have no vetted SQL mapping, transactions, and non-literal queries.
- Validation annotations other than plain `@Required` and integer length annotations.
- Namespace changes outside the supported `javax.*` package list.
- Spring Boot configuration conversion, dicon, XML descriptors other than `web.xml`, JSP custom tags, or TLD transformation.
- Compile or runtime validation.
- Batch execution across a plan.

Those require separate vetted rules with apply conditions, tests, and validation. The engine rule catalog is currently eleven rules; unsupported items remain explicitly unsupported. The domain-by-domain close-out audit and its boundary evidence live in [10-delivery-plan.md](10-delivery-plan.md#phase-4-close-out-audit); cross-file edit safety is in [15-cross-file-config.md](15-cross-file-config.md).
