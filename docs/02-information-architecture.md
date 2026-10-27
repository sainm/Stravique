# Phase 1 Pages and Interactions

## Global shell

The persistent left rail contains brand, project navigation, language selection, and runtime safeguards. Project routes share a compact project switcher and a visible read-only badge. The main canvas changes layout according to the task instead of forcing one dashboard template.

## Routes

| Route | Purpose | Primary layout |
| --- | --- | --- |
| `/projects` | Project registry and creation | Editorial list with readiness summary |
| `/projects/:projectId/overview` | Current inventory state | Asymmetric overview with findings and history |
| `/projects/:projectId/inventory` | File and evidence exploration | Three-pane workbench |
| `/projects/:projectId/frameworks` | Framework and API usage | Framework index plus evidence table |
| `/projects/:projectId/relations` | Cross-file system relationships | Filter strip plus evidence table |
| `/projects/:projectId/reports` | Evidence-backed exports | Report catalog and export controls |
| `/projects/:projectId/settings` | Project metadata and scan policy | Grouped settings form |

## Projects

- Lists all registered projects with source path, detected stack, last scan, and inventory health.
- Creates a project using a source path processed by the backend.
- Shows the configured allowed scan roots when a path is rejected.
- Does not display pseudo-projects or generated metrics.
- Provides a direct entry to project settings and first scan.

## Overview

- Leads with project identity, latest scan state, and the next useful action.
- Shows file, module, evidence, parse-failure, and review counts as a compact status strip rather than isolated metric cards.
- Shows detected technologies with their certainty state.
- Highlights review-required and unresolved findings.
- Shows the latest scan history and explicit no-scan state.
- Starts or observes scanning without fake progress animation.

## Inventory Explorer

The explorer uses three panes:

1. Repository tree grouped by real directory paths and modules.
2. Virtualized-style paginated file list with search, type, and parse-status filters.
3. Detail inspector with path, metadata, evidence, rule IDs, certainty, and parse state.

Selecting a file loads persisted evidence. Evidence rows show the source range and snippet captured during analysis. Summaries can link directly to a filtered file or framework result. Empty and failed states explain what is missing.

## Framework Usage

- Lists only technologies persisted for the selected scan.
- Distinguishes dependency declarations, parsed API usage, inferred relationships, and uncertainty.
- Shows framework evidence grouped by file and source range.
- Explains when a framework is declared but no usage was observed.
- Supports `SAStruts` and Struts 1 as separate technology keys.

## Reports

Supported report families:

- File inventory.
- Project structure.
- Technology stack.
- Framework API usage.
- Configuration inventory.
- Parse failures.
- Review and unresolved items.

Each report exports CSV, JSON, or Markdown from the same persisted query used by the preview count. Export actions create an audit record. There are no client-side invented totals.

## Settings

- Edits project name, description, detected project type, JDK, build tool, source framework, target framework, exclusions, and analysis scope.
- Displays backend-owned source path policy.
- Adds and removes explicit directory-role overrides for unresolved source, web, resource, configuration, SQL, and test roots.
- Clearly states that correcting metadata does not rewrite source files.

## Responsive behavior

- At desktop width, the shell keeps a 232 px rail and explorer uses a 22% / 38% / 40% pane split.
- At medium width, the rail becomes compact and the inspector becomes a slide-over.
- On narrow width, project navigation becomes a drawer, explorer panes become sequential views, and tables become labeled stacks.
- Japanese and Chinese labels are allowed to wrap without changing route or state values.

## Error and empty behavior

- API errors use stable codes and localized messages.
- A failed scan remains failed; retry creates a new scan record.
- Cancelled scans remain visible with partial scan metadata and no completion claims.
- A source path outside the backend allowlist is rejected before any directory walk.
