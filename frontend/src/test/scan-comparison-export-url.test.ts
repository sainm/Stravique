import { describe, expect, it } from "vitest";
import { api } from "../lib/api";

describe("scan comparison export URLs", () => {
  it("carries the base scan, format, and active interface language", () => {
    expect(
      api.scanComparisonExportUrl("project-1", "scan-2", "scan-1", "markdown", "ja-JP")
    ).toBe(
      "/api/v1/projects/project-1/scans/scan-2/comparison/export?baseScanId=scan-1&format=markdown&lang=ja-JP"
    );
  });

  it("omits the language when none is active", () => {
    expect(
      api.scanComparisonExportUrl("project-1", "scan-2", "scan-1", "json")
    ).toBe(
      "/api/v1/projects/project-1/scans/scan-2/comparison/export?baseScanId=scan-1&format=json"
    );
  });
});
