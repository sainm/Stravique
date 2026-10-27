# Stravique Phase 1 Product Scope

## Product statement

Stravique is a local-first inventory and analysis workbench for legacy Java web systems. Phase 1 proves one narrow promise:

> Given an existing Java web source tree, produce repeatable, evidence-linked inventory results without modifying the source tree.

Code transformation, a graph database, and AI-generated migration decisions are deliberately outside Phase 1.

## Primary users

- Engineers assessing the size and shape of an inherited Java web system.
- Modernization leads who need evidence before planning migration work.
- Reviewers who need to distinguish confirmed API usage from dependency declarations and unknowns.

## Phase 1 goals

1. Register multiple analysis projects and correct automatically detected project metadata.
2. Scan a local source directory through a controlled backend process.
3. Classify supported source, configuration, web, and test files.
4. Identify modules, source roots, web roots, resource roots, and build tools.
5. Detect common legacy technology signals and framework API usage.
6. Persist exact file, line, snippet, rule, analyzer, timestamp, and certainty evidence.
7. Expose overview, explorer, framework usage, and report experiences.
8. Report parse failures and review-required conditions without inventing relationships.
9. Export reports from persisted scan data in CSV, JSON, and Markdown.
10. Support English, Simplified Chinese, and Japanese UI resources.

## Explicitly out of scope

- Source code modification or automated migration.
- Complete runtime dependency resolution.
- Complete call graphs, data flow, or control flow.
- JSP rendering or runtime framework execution.
- AI interpretation treated as verified fact.
- User accounts, remote multi-tenant hosting, or cloud file access.
- A graph database or graph-first UI.
- Claims of runtime usage from declaration-only dependency evidence.

## Certainty contract

Every finding carries one of these states:

| State | Meaning |
| --- | --- |
| `IDENTIFIED` | A declaration or artifact was directly identified, but usage is not established. |
| `ANALYZED` | A supported static parser directly observed the construct. |
| `INFERRED` | A rule suggests a relationship that requires contextual confirmation. |
| `REVIEW_REQUIRED` | A human decision is required before relying on the result. |
| `UNRESOLVED` | Dynamic or unsupported behavior prevents a reliable answer. |
| `PARSE_FAILED` | The intended parser could not produce a trustworthy result. |

These states are not interchangeable. A Maven dependency is `IDENTIFIED`, not `ANALYZED`. A parsed Java import is `ANALYZED`, but it still does not prove that a branch executes at runtime.

## Product invariants

- Inventory scans are read-only.
- Source paths are resolved through the backend and constrained by an allowlist.
- Symlinks are not followed.
- Results are persisted before they are displayed.
- Exports read persisted results only.
- Missing data remains missing; it is never filled with sample values.
- Technical labels and stable enums do not change with UI language. Exported column names and cell values are technical labels; only the Markdown document renders localized titles and column labels.
- The browser receives relative repository paths, not arbitrary filesystem access.

## Success condition

Phase 1 is accepted only after a known legacy fixture and the shipped sample project can be scanned repeatedly, inspected down to source line evidence, reviewed in all three languages, exported, and shown to remain byte-for-byte unchanged by inventory operations.
