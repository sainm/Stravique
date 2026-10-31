# Phase 14 Transformation Backup Ledger and Safe Pruning

## Purpose

Every applied edit writes a byte-for-byte backup outside the project before the atomic write. Over time those files accumulate. Phase 14 adds a project-level ledger and a deliberately narrow cleanup:

- The ledger shows every edit run with a backup, whether the file still exists, its size, the run status, and whether it is restorable or prunable.
- `APPLIED` backups are protected because they are the only source for restore.
- Only backups belonging to `RESTORED` runs can be pruned; a restored run cannot be restored again.
- Pruning requires explicit confirmation and writes an audit event.

The API never returns backup paths. The ledger uses the configured external backup directory and rejects a directory inside the project exactly like apply does.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/projects/{id}/transformation-backups` | Project backup ledger |
| `POST` | `/api/v1/projects/{id}/transformation-backups/prune` | Delete backups of restored runs with confirmation |

The ledger summary contains `total`, `existing`, `missing`, `restorable`, `prunable`, and `totalBytes`. Each item contains the run, plan, item, title, relative path, run status, rule/version, existence, size, restorable/prunable flags, and timestamps.

`restorable` means the run is `APPLIED` and its backup exists; restore still rechecks source and target hashes. `prunable` means the run is `RESTORED` and its backup exists.

## Safety

- Only paths under the configured backup root and outside the project are considered.
- A missing backup is reported, not recreated.
- Pruning deletes only regular files for `RESTORED` runs; `APPLIED` and unknown statuses are untouched.
- A failed delete is reported; one failure does not stop the remaining deletes.
- The audit event records requested, deleted, and failed counts.

## UI

The Migration plan view shows a backup panel after the coverage panel:

- Metrics for total, restorable, prunable, and missing backups, plus stored size.
- A list of recent backups with title, path, run status, existence, size, and prunable state.
- A confirmation dialog for pruning restored backups; the dialog states that `APPLIED` backups are protected.

Three languages are supported. The panel is visible for the selected project, independent of the selected plan's coverage.

## Verification

- Integration test applies one edit and restores another, then reads the ledger, rejects prune without confirmation, prunes restored backups, verifies the applied backup remains and the restored backup is missing, and checks the audit event.
- The end-to-end stage asserts an empty ledger for a freshly planned fixture (no transformations applied).
- The browser check covers the backup panel in three languages and both viewports.
