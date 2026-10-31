import type {
  MigrationPlanComparisonItem,
  MigrationPlanSummary
} from "./types";

export const COMPARISON_STATUSES = ["ADDED", "REMOVED", "CHANGED"] as const;

export type ComparisonStatus = (typeof COMPARISON_STATUSES)[number];

export interface ComparisonGroup {
  status: ComparisonStatus;
  items: MigrationPlanComparisonItem[];
}

export type ComparisonTranslator = (
  key: string,
  options?: Record<string, unknown>
) => string;

export function groupComparisonItems(
  items: MigrationPlanComparisonItem[]
): ComparisonGroup[] {
  return COMPARISON_STATUSES.map((status) => ({
    status,
    items: items.filter((item) => item.status === status)
  })).filter((group) => group.items.length > 0);
}

export function comparisonBaseOptions(
  plans: MigrationPlanSummary[],
  currentPlanId: string
): MigrationPlanSummary[] {
  return plans
    .filter((plan) => plan.id !== currentPlanId)
    .sort((left, right) => right.sequence - left.sequence);
}

export function defaultComparisonBase(
  plans: MigrationPlanSummary[],
  current: MigrationPlanSummary
): string {
  const options = comparisonBaseOptions(plans, current.id);
  const previous = options.find((plan) => plan.sequence === current.sequence - 1);
  return previous?.id ?? options[0]?.id ?? "";
}

export function comparisonValue(
  field: string,
  value: string | undefined,
  t: ComparisonTranslator
): string {
  if (value === undefined || value === null || value === "") {
    return t("migration.compare.none");
  }
  switch (field) {
    case "INCLUDED":
      return value === "true"
        ? t("migration.included")
        : t("migration.excluded");
    case "STATUS":
      return t(`status.migration.${value}`, { defaultValue: value });
    case "RISK":
      return t(`migration.risks.${value}`, { defaultValue: value });
    case "AUTOMATION":
      return t(`migration.automations.${value}`, { defaultValue: value });
    case "PHASE":
      return t("migration.compare.phaseValue", { number: value });
    default:
      return value;
  }
}
