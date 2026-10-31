import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ChevronRight, FileCode2, GitCompare } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import LoadingBlock from "../../components/LoadingBlock";
import Modal from "../../components/Modal";
import { api } from "../../lib/api";
import { formatNumber } from "../../lib/format";
import {
  comparisonBaseOptions,
  comparisonValue,
  defaultComparisonBase,
  groupComparisonItems,
  type ComparisonTranslator
} from "../../lib/migrationComparison";
import type { MigrationPlanSummary } from "../../lib/types";

interface MigrationPlanComparisonModalProps {
  projectId: string;
  plan: MigrationPlanSummary;
  plans: MigrationPlanSummary[];
  open: boolean;
  onClose: () => void;
  onOpenItem: (itemId: string) => void;
}

export default function MigrationPlanComparisonModal({
  projectId,
  plan,
  plans,
  open,
  onClose,
  onOpenItem
}: MigrationPlanComparisonModalProps) {
  const { t, i18n } = useTranslation();
  const translate = t as unknown as ComparisonTranslator;
  const optionsQuery = useQuery({
    queryKey: ["migration-plan-options", projectId],
    queryFn: () => api.migrationPlanOptions(projectId),
    enabled: open && Boolean(projectId)
  });
  const allPlans = optionsQuery.data ?? plans;
  const baseOptions = comparisonBaseOptions(allPlans, plan.id);
  const defaultBaseId = defaultComparisonBase(allPlans, plan);
  const [basePlanId, setBasePlanId] = useState("");
  useEffect(() => {
    if (open) {
      setBasePlanId(defaultBaseId);
    }
  }, [open, plan.id, defaultBaseId]);
  const comparisonQuery = useQuery({
    queryKey: [
      "migration-plan-comparison",
      projectId,
      plan.id,
      basePlanId || "previous"
    ],
    queryFn: () =>
      api.migrationPlanComparison(
        projectId,
        plan.id,
        basePlanId || undefined
      ),
    enabled:
      open &&
      Boolean(projectId) &&
      Boolean(plan.id) &&
      Boolean(basePlanId || baseOptions.length === 0)
  });
  const comparison = comparisonQuery.data;
  const groups = comparison ? groupComparisonItems(comparison.items) : [];

  return (
    <Modal
      open={open}
      title={t("migration.compare.title")}
      description={
        comparison
          ? t("migration.compare.description", {
              current: comparison.plan.sequence,
              base: comparison.basePlan.sequence
            })
          : t("migration.compare.descriptionPending")
      }
      onClose={onClose}
    >
      {comparisonQuery.isLoading ? <LoadingBlock /> : null}
      {comparisonQuery.error ? (
        <p className="inline-error">{comparisonQuery.error.message}</p>
      ) : null}
      {comparison ? (
        <div className="migration-comparison-body">
          {baseOptions.length > 0 ? (
            <label className="migration-comparison-base">
              <span>{t("migration.compare.baseLabel")}</span>
              <select
                value={basePlanId}
                onChange={(event) => setBasePlanId(event.target.value)}
              >
                {baseOptions.map((option) => (
                  <option key={option.id} value={option.id}>
                    {t("migration.compare.planOption", {
                      number: option.sequence
                    })}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <p className="migration-note">
              {t("migration.compare.noBasePlans")}
            </p>
          )}
          <div className="migration-comparison-metrics">
            {(["added", "removed", "changed", "unchanged"] as const).map((key) => (
              <div
                key={key}
                className={`migration-comparison-metric migration-comparison-${key}`}
              >
                <span>{t(`migration.compare.metrics.${key}`)}</span>
                <strong>
                  {formatNumber(comparison.summary[key], i18n.language)}
                </strong>
              </div>
            ))}
          </div>
          {groups.length === 0 ? (
            <div className="migration-comparison-empty">
              <GitCompare size={18} />
              <span>{t("migration.compare.empty")}</span>
            </div>
          ) : null}
          {groups.map((group) => (
            <section key={group.status} className="migration-comparison-group">
              <header>
                <span
                  className={`migration-diff-status migration-diff-${group.status.toLowerCase()}`}
                >
                  {t(`migration.compare.status.${group.status}`)}
                </span>
                <span className="migration-comparison-count">
                  {t("migration.compare.groupCount", {
                    count: group.items.length
                  })}
                </span>
              </header>
              <div className="migration-comparison-list">
                {group.items.map((item) => (
                  <article
                    key={`${group.status}-${item.itemKey}`}
                    className="migration-comparison-row"
                  >
                    <div className="migration-comparison-title">
                      <div className="migration-comparison-title-head">
                        <strong>{item.title}</strong>
                        {item.itemId ? (
                          <button
                            type="button"
                            className="migration-comparison-open"
                            onClick={() => onOpenItem(item.itemId as string)}
                          >
                            {t("migration.compare.openItem")}
                            <ChevronRight size={12} />
                          </button>
                        ) : (
                          <span className="migration-comparison-removed-note">
                            {t("migration.compare.removedNote")}
                          </span>
                        )}
                      </div>
                      <div className="migration-row-facts">
                        <span className="migration-chip">
                          {t(`migration.itemTypes.${item.itemType}`, {
                            defaultValue: item.itemType
                          })}
                        </span>
                        <span className="migration-chip">
                          <FileCode2 size={12} />
                          {item.primaryFilePath || t("migration.compare.noFile")}
                        </span>
                        <span className="migration-chip">
                          {t("migration.compare.phaseValue", {
                            number: item.phase.toString()
                          })}
                        </span>
                      </div>
                    </div>
                    {item.changes.length > 0 ? (
                      <ul className="migration-comparison-changes">
                        {item.changes.map((change) => (
                          <li key={change.field}>
                            <span className="migration-comparison-field">
                              {t(`migration.compare.fields.${change.field}`, {
                                defaultValue: change.field
                              })}
                            </span>
                            <code>
                              {comparisonValue(
                                change.field,
                                change.before,
                                translate
                              )}
                            </code>
                            <ArrowRight size={12} />
                            <code>
                              {comparisonValue(
                                change.field,
                                change.after,
                                translate
                              )}
                            </code>
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      ) : null}
    </Modal>
  );
}
