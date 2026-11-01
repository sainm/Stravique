import { describe, expect, it } from "vitest";
import { api } from "../lib/api";

describe("migration plan export URLs", () => {
  it("carries format and the active interface language", () => {
    expect(
      api.migrationPlanExportUrl("project-1", "plan-2", "markdown", "ja-JP")
    ).toBe(
      "/api/v1/projects/project-1/migration-plans/plan-2/export?format=markdown&lang=ja-JP"
    );
  });

  it("omits unavailable plan context safely", () => {
    expect(
      api.migrationPlanExportUrl("project-1", "plan-2", "json")
    ).toBe(
      "/api/v1/projects/project-1/migration-plans/plan-2/export?format=json"
    );
  });
});
