# Phase 6A Cross-File Configuration Editing

## Purpose

Phase 6A introduces the first vetted rule whose target file is not the migration item's primary file:

- The item is the `struts-config.xml` configuration item.
- The rule writes `spring.messages.basename` into the module's existing `application.yml` or `application.properties`.

The rule is intentionally additive. It does not remove the Struts declaration and does not modify the source `struts-config.xml`.

## Two-file safety contract

A proposal and its run now carry two hashes:

| Hash | Meaning |
| --- | --- |
| `originalSha256` | Current hash of the item's primary source file |
| `targetSha256` | Current hash of the file that apply will write; null for `CREATE_FILE` |

Apply for an edit:

- Requires the source hash to equal `originalSha256` and the target hash to equal `targetSha256`.
- Backs up the target bytes outside the project.
- Writes the target atomically.

Restore for an edit:

- Refuses when the target no longer matches `appliedSha256`.
- Refuses when the item's source file no longer matches `originalSha256`, so a run cannot be undone underneath an edited declaration.
- Writes the backup bytes back to the target. The source is never written by apply or restore.

The run response exposes `backupPath` beside both hashes, so the target backup can be inspected or archived before it is pruned.

Proposal generation recomputes the target hash from the current target file. A target change between proposal and apply is rejected as `SOURCE_CHANGED`, just like a source change. For same-file rules, the source and target hashes are equal.

## Rule: Struts message-resources → Spring messages

| Property | Value |
| --- | --- |
| Rule ID | `STRUTS-CONFIG-MESSAGE-SPRING` |
| Version | `1.0.0` |
| Source item | `CONFIGURATION / SPRING_BOOT_CONFIG` named `struts-config.xml` |
| Target | `application.yml`, `application.yaml`, or `application.properties` |

Supported output:

- Reads the explicit `<message-resources parameter="...">` value.
- Appends `spring.messages.basename: "<value>"` to YAML, or `spring.messages.basename=<value>` to properties.
- Preserves the target's existing content and line endings.
- Validates the source as XML and the target as YAML or properties.

Rejected for manual review:

- No `message-resources` declaration or a blank parameter.
- No application config target, or more than one candidate (ambiguous).
- An existing `spring.messages.basename` in the target.
- Malformed XML or an unsupported target type.
- Removing the old Struts declaration, dicon AOP/aspect conversion, complete filter/listener behavior migration, and full Spring Boot config generation remain manual.

## Catalog and UI

The rule declares the `struts-config.xml` file-name hint, so the catalog does not offer it for `web.xml` or dicon items. When a proposal targets a different file, the inspector shows a Target file chip, the target path note, and a target-write warning instead of the source-write warning. The confirmation text says the target config file is written.

## Verification

- Unit tests cover YAML, properties, missing/ambiguous targets, existing keys, missing message-resources, malformed XML, and item/file support.
- Integration tests preview, externally modify the target to prove rejection, apply, confirm the source is byte-identical, restore, and confirm both files return to their original bytes.
- The rule catalog test asserts the rule ID, YAML language, and file-name hint.
- Browser checks cover the cross-file inspector in three languages and both viewports.
