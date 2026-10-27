import { describe, expect, it } from "vitest";
import { api } from "../lib/api";

describe("report export URLs", () => {
  it("carries the active interface language", () => {
    const url = api.reportUrl("project-1", "FILES", "csv", "scan-1", "zh-CN");

    expect(url).toBe("/api/v1/projects/project-1/reports/FILES.csv?scanId=scan-1&lang=zh-CN");
  });

  it("omits absent query parameters", () => {
    expect(api.reportUrl("project-1", "REVIEWS", "markdown")).toBe(
      "/api/v1/projects/project-1/reports/REVIEWS.markdown"
    );
  });

  it("keeps the scan identifier when only the language is provided", () => {
    const url = api.reportUrl("project-1", "TECHNOLOGY", "json", undefined, "ja-JP");

    expect(url).toBe("/api/v1/projects/project-1/reports/TECHNOLOGY.json?lang=ja-JP");
  });

  it("preserves the requested format and report type verbatim", () => {
    const url = api.reportUrl("abc", "PARSE_ERRORS", "csv", "scan-9", "en-US");

    expect(url).toContain("/reports/PARSE_ERRORS.csv");
    expect(url).toContain("scanId=scan-9");
    expect(url).toContain("lang=en-US");
  });
});
