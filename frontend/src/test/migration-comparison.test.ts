import { describe, expect, it } from "vitest";
import {
  comparisonBaseOptions,
  comparisonValue,
  defaultComparisonBase,
  groupComparisonItems
} from "../lib/migrationComparison";
import type {
  MigrationPlanComparisonItem,
  MigrationPlanSummary
} from "../lib/types";

const translator = (key: string, options?: Record<string, unknown>) => {
  if (options?.number !== undefined) {
    return `${key}:${options.number}`;
  }
  if (options?.defaultValue !== undefined) {
    return `${key}:${options.defaultValue}`;
  }
  return key;
};

const item = (
  status: string,
  overrides: Partial<MigrationPlanComparisonItem> = {}
): MigrationPlanComparisonItem => ({
  status,
  itemKey: `${status}-key`,
  itemType: "CONFIGURATION",
  targetKey: "SPRING_BOOT_CONFIG",
  title: `${status} item`,
  phase: 1,
  automationLevel: "ASSISTED",
  riskLevel: "MEDIUM",
  itemStatus: "READY",
  included: true,
  changes: [],
  ...overrides
});

const plan = (
  id: string,
  sequence: number,
  overrides: Partial<MigrationPlanSummary> = {}
): MigrationPlanSummary => ({
  id,
  projectId: "project",
  scanId: "scan",
  sequence,
  sourceProfile: "SASTRUTS",
  targetProfile: "SPRING_BOOT_MVC",
  sourceScanSequence: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  itemCount: 1,
  includedCount: 1,
  readyCount: 1,
  reviewCount: 0,
  blockedCount: 0,
  dependencyCount: 0,
  phaseCount: 1,
  analyzerVersion: "1.0.0",
  stale: false,
  ...overrides
});

describe("migration comparison helpers", () => {
  it("groups rows in added, removed, changed order and drops empty groups", () => {
    const groups = groupComparisonItems([
      item("CHANGED"),
      item("ADDED"),
      item("ADDED")
    ]);

    expect(groups.map((group) => group.status)).toEqual(["ADDED", "CHANGED"]);
    expect(groups[0].items).toHaveLength(2);
    expect(groups[1].items).toHaveLength(1);
  });

  it("formats field values through the supplied translator", () => {    expect(comparisonValue("INCLUDED", "true", translator)).toBe(
      "migration.included"
    );
    expect(comparisonValue("INCLUDED", "false", translator)).toBe(
      "migration.excluded"
    );
    expect(comparisonValue("STATUS", "READY", translator)).toBe(
      "status.migration.READY:READY"
    );
    expect(comparisonValue("RISK", "HIGH", translator)).toBe(
      "migration.risks.HIGH:HIGH"
    );
    expect(comparisonValue("AUTOMATION", "MANUAL", translator)).toBe(
      "migration.automations.MANUAL:MANUAL"
    );
    expect(comparisonValue("PHASE", "3", translator)).toBe(
      "migration.compare.phaseValue:3"
    );
    expect(comparisonValue("TITLE", "Order action", translator)).toBe(
      "Order action"
    );
    expect(comparisonValue("PRIMARY_FILE", undefined, translator)).toBe(
      "migration.compare.none"
    );
  });

  it("offers other plan versions newest first and defaults to the previous one", () => {
    const current = plan("current", 5);
    const plans = [
      plan("older", 3),
      current,
      plan("previous", 4),
      plan("newer", 6)
    ];

    expect(comparisonBaseOptions(plans, current.id).map((entry) => entry.id)).toEqual([
      "newer",
      "previous",
      "older"
    ]);
    expect(defaultComparisonBase(plans, current)).toBe("previous");
    expect(
      defaultComparisonBase([current, plan("older", 3)], current)
    ).toBe("older");
    expect(defaultComparisonBase([current], current)).toBe("");
  });
});
