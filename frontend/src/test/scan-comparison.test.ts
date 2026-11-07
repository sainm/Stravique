import { describe, expect, it } from "vitest";
import {
  completedScans,
  defaultScanComparisonBase,
  defaultScanComparisonCurrent,
  groupScanComparisonRows,
  shortHash
} from "../lib/scanComparison";
import type { Scan, ScanComparisonRow } from "../lib/types";

const scan = (
  id: string,
  sequence: number,
  status = "COMPLETED",
  overrides: Partial<Scan> = {}
): Scan => ({
  id,
  sequence,
  status,
  createdAt: "2026-01-01T00:00:00Z",
  fileCount: 10,
  moduleCount: 1,
  analyzedCount: 10,
  parseFailureCount: 0,
  skippedCount: 0,
  findingCount: 0,
  reviewCount: 0,
  technologyCount: 0,
  removedCount: 0,
  analyzerVersion: "1.0.0",
  ...overrides
});

const row = (
  status: string,
  relativePath: string
): ScanComparisonRow => ({
  status,
  relativePath
});

describe("scan comparison helpers", () => {
  it("groups rows in added, removed, changed, unknown order", () => {
    const groups = groupScanComparisonRows([
      row("UNKNOWN", "d.java"),
      row("CHANGED", "c.java"),
      row("ADDED", "a.java"),
      row("ADDED", "b.java")
    ]);

    expect(groups.map((group) => group.status)).toEqual([
      "ADDED",
      "CHANGED",
      "UNKNOWN"
    ]);
    expect(groups[0].rows.map((entry) => entry.relativePath)).toEqual([
      "a.java",
      "b.java"
    ]);
  });

  it("keeps only completed scans and offers every other scan as a base", () => {
    const scans = [
      scan("current", 4),
      scan("failed", 5, "FAILED"),
      scan("previous", 3),
      scan("older", 2)
    ];

    expect(completedScans(scans).map((entry) => entry.id)).toEqual([
      "current",
      "previous",
      "older"
    ]);
    expect(
      groupScanComparisonRows([]).map((group) => group.status)
    ).toEqual([]);
  });

  it("defaults the current scan to the latest completed and the base to the previous sequence", () => {
    const scans = [
      scan("latest", 6),
      scan("previous", 5),
      scan("older", 3),
      scan("failed", 7, "FAILED")
    ];

    expect(defaultScanComparisonCurrent(scans)).toBe("latest");
    expect(defaultScanComparisonCurrent(scans, "older")).toBe("older");
    expect(defaultScanComparisonCurrent(scans, "failed")).toBe("latest");
    expect(defaultScanComparisonBase(scans, "latest")).toBe("previous");
    expect(defaultScanComparisonBase(scans, "previous")).toBe("older");
    expect(defaultScanComparisonBase(scans, "older")).toBe("latest");
  });

  it("shortens content hashes and keeps a missing hash empty", () => {
    expect(shortHash("0123456789abcdef")).toBe("0123456789ab");
    expect(shortHash(undefined)).toBe("");
  });
});
