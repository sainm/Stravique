import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  ArrowRight,
  ChevronRight,
  Clock3,
  GitBranch,
  History,
  Layers,
  ListChecks,
  Play,
  RefreshCw,
  RotateCcw,
  Route,
  ShieldAlert,
  Sparkles
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams, useSearchParams } from "react-router-dom";
import EmptyState from "../../components/EmptyState";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import Modal from "../../components/Modal";
import PageHeader from "../../components/PageHeader";
import StatusBadge from "../../components/StatusBadge";
import { api } from "../../lib/api";
import { formatDate, formatNumber } from "../../lib/format";
import type {
  MigrationGraph,
  MigrationItem,
  MigrationPlanSummary,
  TransformationBatch
} from "../../lib/types";
import MigrationGraphView from "./MigrationGraphView";
import MigrationInspector from "./MigrationInspector";

type View = "plan" | "phases" | "dependencies" | "review" | "history";

const VIEWS: View[] = ["plan", "phases", "dependencies", "review", "history"];
const TARGET_PROFILES = ["SPRING_BOOT_MVC"];
const ITEM_TYPES = [
  "ACTION_CONTROLLER",
  "FORM_MODEL",
  "COMPONENT_BEAN",
  "DATA_ACCESS",
  "TRANSACTION",
  "MESSAGE_FLOW",
  "MESSAGE_RESOURCE",
  "VALIDATION",
  "JAKARTA_NAMESPACE",
  "JSP_VIEW",
  "CONFIGURATION",
  "REVIEW"
];

export default function MigrationPage() {
  const { projectId = "" } = useParams();
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [queryText, setQueryText] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [scopeFilter, setScopeFilter] = useState("");
  const [page, setPage] = useState(0);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [targetProfile, setTargetProfile] = useState(TARGET_PROFILES[0]);
  const [batchIds, setBatchIds] = useState<Set<string>>(() => new Set());
  const [batchResult, setBatchResult] = useState<TransformationBatch | null>(null);
  const [batchResultOpen, setBatchResultOpen] = useState(false);
  const [batchApplyOpen, setBatchApplyOpen] = useState(false);
  const [batchConfirm, setBatchConfirm] = useState(false);
  const [batchRestoreConfirm, setBatchRestoreConfirm] = useState(false);
  const [batchError, setBatchError] = useState<Error | null>(null);

  const projectQuery = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => api.project(projectId)
  });
  const scansQuery = useQuery({
    queryKey: ["scans", projectId],
    queryFn: () => api.scans(projectId)
  });
  const plansQuery = useQuery({
    queryKey: ["migration-plans", projectId],
    queryFn: () => api.migrationPlans(projectId)
  });

  const plans = plansQuery.data || [];
  const requestedPlanId = searchParams.get("plan") || "";
  const selectedPlanId = requestedPlanId || plans[0]?.id || "";
  const requestedView = searchParams.get("view") || "plan";
  const view: View = VIEWS.includes(requestedView as View)
    ? (requestedView as View)
    : "plan";

  useEffect(() => {
    setBatchIds(new Set());
    setBatchResult(null);
    setBatchResultOpen(false);
    setBatchApplyOpen(false);
    setBatchConfirm(false);
    setBatchRestoreConfirm(false);
    setBatchError(null);
  }, [selectedPlanId, view]);

  const planQuery = useQuery({
    queryKey: ["migration-plan", projectId, selectedPlanId],
    queryFn: () => api.migrationPlan(projectId, selectedPlanId),
    enabled: Boolean(selectedPlanId)
  });
  const plan = planQuery.data;
  const summary = plan?.summary || plans.find((item) => item.id === selectedPlanId);

  const itemsQuery = useQuery({
    queryKey: [
      "migration-items",
      projectId,
      selectedPlanId,
      queryText,
      statusFilter,
      typeFilter,
      scopeFilter,
      page
    ],
    queryFn: () =>
      api.migrationItems(projectId, selectedPlanId, {
        q: queryText || undefined,
        status: statusFilter || undefined,
        itemType: typeFilter || undefined,
        included:
          scopeFilter === "included"
            ? true
            : scopeFilter === "excluded"
              ? false
              : undefined,
        page,
        size: 20
      }),
    enabled: Boolean(selectedPlanId) && view === "plan"
  });
  const phaseItemsQuery = useQuery({
    queryKey: ["migration-phase-items", projectId, selectedPlanId],
    queryFn: () =>
      api.migrationItems(projectId, selectedPlanId, {
        included: true,
        size: 200
      }),
    enabled: Boolean(selectedPlanId) && view === "phases"
  });
  const reviewQuery = useQuery({
    queryKey: ["migration-review-items", projectId, selectedPlanId],
    queryFn: () =>
      api.migrationItems(projectId, selectedPlanId, {
        attention: true,
        included: true,
        size: 100
      }),
    enabled: Boolean(selectedPlanId) && view === "review"
  });
  const graphQuery = useQuery({
    queryKey: ["migration-graph", projectId, selectedPlanId],
    queryFn: () => api.migrationGraph(projectId, selectedPlanId),
    enabled: Boolean(selectedPlanId) && view === "dependencies"
  });

  const scopeMutation = useMutation({
    mutationFn: (input: { itemId: string; included: boolean }) =>
      api.updateMigrationScope(projectId, selectedPlanId, input.itemId, input.included),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["migration-plan", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["migration-plans", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["migration-items", projectId] }),
        queryClient.invalidateQueries({
          queryKey: ["migration-phase-items", projectId]
        }),
        queryClient.invalidateQueries({
          queryKey: ["migration-review-items", projectId]
        }),
        queryClient.invalidateQueries({ queryKey: ["migration-graph", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["migration-item", projectId] })
      ]);
    }
  });

  const generateMutation = useMutation({
    mutationFn: () =>
      api.generateMigrationPlan(projectId, {
        targetProfile
      }),
    onSuccess: async (data) => {
      const next = new URLSearchParams(searchParams);
      next.set("plan", data.summary.id);
      next.set("view", "plan");
      setSearchParams(next, { replace: true });
      setGenerateOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["migration-plans", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["migration-plan", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["migration-items", projectId] }),
        queryClient.invalidateQueries({
          queryKey: ["migration-phase-items", projectId]
        }),
        queryClient.invalidateQueries({
          queryKey: ["migration-review-items", projectId]
        }),
        queryClient.invalidateQueries({ queryKey: ["migration-graph", projectId] })
      ]);
    }
  });

  const refreshAfterBatch = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["migration-plans", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["migration-plan", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["migration-items", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["migration-phase-items", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["migration-review-items", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["migration-graph", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["migration-item", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["transformation-proposals", projectId] }),
      queryClient.invalidateQueries({ queryKey: ["transformation-runs", projectId] })
    ]);
  };

  const batchPreviewMutation = useMutation({
    mutationFn: () =>
      api.previewTransformationBatch(projectId, selectedPlanId, Array.from(batchIds)),
    onSuccess: async (data) => {
      setBatchResult(data);
      setBatchResultOpen(true);
      setBatchRestoreConfirm(false);
      setBatchError(null);
      await queryClient.invalidateQueries({
        queryKey: ["transformation-proposals", projectId]
      });
    },
    onError: (error) => setBatchError(error as Error)
  });

  const batchApplyMutation = useMutation({
    mutationFn: () =>
      api.applyTransformationBatch(projectId, selectedPlanId, Array.from(batchIds)),
    onSuccess: async (data) => {
      setBatchResult(data);
      setBatchApplyOpen(false);
      setBatchResultOpen(true);
      setBatchConfirm(false);
      setBatchRestoreConfirm(false);
      setBatchIds(new Set());
      await refreshAfterBatch();
    },
    onError: (error) => setBatchError(error as Error)
  });

  const batchRestoreMutation = useMutation({
    mutationFn: (runIds: string[]) =>
      api.restoreTransformationBatch(projectId, selectedPlanId, runIds),
    onSuccess: async (data) => {
      setBatchResult(data);
      setBatchResultOpen(true);
      setBatchRestoreConfirm(false);
      await refreshAfterBatch();
    },
    onError: (error) => setBatchError(error as Error)
  });

  const latestCompleted = useMemo(
    () => scansQuery.data?.find((scan) => scan.status === "COMPLETED"),
    [scansQuery.data]
  );

  const updateParams = (updates: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(updates)) {
      if (value === null || value === "") {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    }
    setSearchParams(next, { replace: true });
  };

  const selectPlan = (planId: string) => {
    setSelectedItemId(null);
    setPage(0);
    updateParams({ plan: planId, view: "plan" });
  };

  const selectView = (nextView: View) => {
    setPage(0);
    updateParams({ view: nextView });
  };

  const resetFilters = () => {
    setQueryText("");
    setStatusFilter("");
    setTypeFilter("");
    setScopeFilter("");
    setPage(0);
  };

  const toggleBatch = (itemId: string, selected: boolean) => {
    setBatchIds((previous) => {
      const next = new Set(previous);
      if (selected) {
        next.add(itemId);
      } else {
        next.delete(itemId);
      }
      return next;
    });
    setBatchError(null);
  };

  const clearBatch = () => {
    setBatchIds(new Set());
    setBatchError(null);
  };

  const appliedRunIds =
    batchResult?.items
      .filter((item) => item.run?.status === "APPLIED")
      .map((item) => item.run?.id)
      .filter((id): id is string => Boolean(id)) ?? [];

  const scopeError =
    scopeMutation.error instanceof Error
      ? scopeMutation.error.message
      : undefined;

  if (projectQuery.isLoading || plansQuery.isLoading) {
    return <LoadingBlock />;
  }
  if (projectQuery.error) {
    return (
      <ErrorPanel
        error={projectQuery.error}
        onRetry={() => projectQuery.refetch()}
      />
    );
  }
  if (plansQuery.error) {
    return (
      <ErrorPanel error={plansQuery.error} onRetry={() => plansQuery.refetch()} />
    );
  }
  if (!projectQuery.data) {
    return null;
  }

  const hasCompletedScan = Boolean(latestCompleted);
  const planUnavailable =
    Boolean(selectedPlanId) && planQuery.isError;
  const attentionCount = summary
    ? summary.reviewCount + summary.blockedCount
    : 0;

  return (
    <>
      <PageHeader
        eyebrow={t("migration.eyebrow")}
        title={t("migration.title")}
        description={t("migration.description")}
        actions={
          <div className="header-action-group">
            {summary ? (
              <button
                className="button button-ghost"
                onClick={() => {
                  void planQuery.refetch();
                  void plansQuery.refetch();
                }}
                aria-label={t("common.refresh")}
              >
                <RefreshCw size={15} />
                {t("common.refresh")}
              </button>
            ) : null}
            <button
              className="button button-primary"
              onClick={() => setGenerateOpen(true)}
              disabled={!hasCompletedScan || generateMutation.isPending}
            >
              <Sparkles size={16} />
              {summary ? t("migration.regenerate") : t("migration.generate")}
            </button>
          </div>
        }
      />

      {!selectedPlanId ? (
        <section className="surface migration-empty">
          {!hasCompletedScan ? (
            <EmptyState
              icon={Route}
              title={t("migration.noCompletedScanTitle")}
              description={t("migration.noCompletedScanDescription")}
              action={
                <Link className="button button-secondary" to={`/projects/${projectId}/overview`}>
                  {t("migration.goToOverview")}
                  <ArrowRight size={14} />
                </Link>
              }
            />
          ) : (
            <EmptyState
              icon={GitBranch}
              title={t("migration.noPlanTitle")}
              description={t("migration.noPlanDescription")}
              action={
                <button
                  className="button button-primary"
                  onClick={() => setGenerateOpen(true)}
                >
                  <Sparkles size={16} />
                  {t("migration.generate")}
                </button>
              }
            />
          )}
          <div className="scan-facts">
            <div>
              <ShieldAlert size={16} />
              <span>{t("migration.facts.readOnly")}</span>
            </div>
            <div>
              <ListChecks size={16} />
              <span>{t("migration.facts.evidence")}</span>
            </div>
            <div>
              <GitBranch size={16} />
              <span>{t("migration.facts.dependencies")}</span>
            </div>
          </div>
        </section>
      ) : planUnavailable ? (
        <ErrorPanel
          error={planQuery.error}
          onRetry={() => planQuery.refetch()}
        />
      ) : planQuery.isLoading || !summary ? (
        <LoadingBlock />
      ) : (
        <>
          <section className="surface migration-summary">
            <div className="migration-summary-identity">
              <div className="migration-summary-topline">
                <span className="eyebrow">{t("migration.planLabel")}</span>
                <span className="migration-plan-number">
                  {t("migration.planNumber", { number: summary.sequence })}
                </span>
              </div>
              <div className="migration-profile-flow">
                <div>
                  <span className="migration-fact-label">
                    {t("migration.sourceProfile")}
                  </span>
                  <div className="migration-profile-chips">
                    {summary.sourceProfile.split(",").map((profile) => (
                      <code key={profile.trim()}>{profile.trim()}</code>
                    ))}
                  </div>
                </div>
                <ArrowRight size={18} className="migration-profile-arrow" />
                <div>
                  <span className="migration-fact-label">
                    {t("migration.targetProfile")}
                  </span>
                  <div className="migration-profile-chips">
                    <code>
                      {t(`migration.profiles.${summary.targetProfile}`, {
                        defaultValue: summary.targetProfile
                      })}
                    </code>
                  </div>
                </div>
              </div>
              <div className="migration-summary-meta">
                <span>
                  <Clock3 size={14} />
                  {t("migration.generatedAt", {
                    date: formatDate(summary.createdAt, i18n.language)
                  })}
                </span>
                <span>
                  {t("migration.sourceScan", {
                    number: summary.sourceScanSequence
                  })}
                </span>
                <span>{t("migration.includedOf", {
                  included: summary.includedCount,
                  total: summary.itemCount
                })}</span>
              </div>
            </div>
            <div className="migration-metrics" aria-label={t("migration.metricsLabel")}>
              <Metric label={t("migration.metrics.items")} value={summary.includedCount} />
              <Metric label={t("migration.metrics.ready")} value={summary.readyCount} tone="success" />
              <Metric
                label={t("migration.metrics.review")}
                value={summary.reviewCount}
                tone={summary.reviewCount > 0 ? "warning" : "neutral"}
              />
              <Metric
                label={t("migration.metrics.blocked")}
                value={summary.blockedCount}
                tone={summary.blockedCount > 0 ? "danger" : "neutral"}
              />
              <Metric
                label={t("migration.metrics.dependencies")}
                value={summary.dependencyCount}
              />
              <Metric label={t("migration.metrics.phases")} value={summary.phaseCount} />
            </div>
          </section>

          {summary.stale ? (
            <section className="migration-stale">
              <AlertTriangle size={18} />
              <div>
                <strong>{t("migration.staleTitle")}</strong>
                <p>
                  {t("migration.staleDescription", {
                    number: summary.sourceScanSequence
                  })}
                </p>
              </div>
              <button
                className="button button-small button-secondary"
                onClick={() => setGenerateOpen(true)}
              >
                {t("migration.regenerate")}
              </button>
            </section>
          ) : null}

          <div className="migration-workspace">
            <div className="mode-switch migration-tabs" role="tablist">
              <button
                className={view === "plan" ? "mode-active" : ""}
                onClick={() => selectView("plan")}
              >
                <ListChecks size={15} />
                {t("migration.tabs.plan")}
                <span className="mode-count">{summary.itemCount}</span>
              </button>
              <button
                className={view === "phases" ? "mode-active" : ""}
                onClick={() => selectView("phases")}
              >
                <GitBranch size={15} />
                {t("migration.tabs.phases")}
              </button>
              <button
                className={view === "dependencies" ? "mode-active" : ""}
                onClick={() => selectView("dependencies")}
              >
                <Route size={15} />
                {t("migration.tabs.dependencies")}
              </button>
              <button
                className={view === "review" ? "mode-active" : ""}
                onClick={() => selectView("review")}
              >
                <ShieldAlert size={15} />
                {t("migration.tabs.review")}
                {attentionCount > 0 ? (
                  <span className="mode-count mode-count-warning">{attentionCount}</span>
                ) : null}
              </button>
              <button
                className={view === "history" ? "mode-active" : ""}
                onClick={() => selectView("history")}
              >
                <History size={15} />
                {t("migration.tabs.history")}
              </button>
            </div>

            {view === "plan" ? (
              <section className="surface migration-list">
                <header className="section-header">
                  <div>
                    <span className="eyebrow">{t("migration.itemsEyebrow")}</span>
                    <h2>{t("migration.itemsTitle")}</h2>
                  </div>
                  <span className="migration-count">
                    {itemsQuery.data
                      ? t("migration.itemCount", {
                          count: itemsQuery.data.totalElements
                        })
                      : null}
                  </span>
                </header>
                <div className="migration-filters">
                  <label className="search-field">
                    <Sparkles size={15} />
                    <input
                      value={queryText}
                      onChange={(event) => {
                        setQueryText(event.target.value);
                        setPage(0);
                      }}
                      placeholder={t("migration.searchPlaceholder")}
                      aria-label={t("migration.searchPlaceholder")}
                    />
                  </label>
                  <label className="select-field">
                    <select
                      value={statusFilter}
                      onChange={(event) => {
                        setStatusFilter(event.target.value);
                        setPage(0);
                      }}
                      aria-label={t("migration.filters.status")}
                    >
                      <option value="">{t("migration.filters.allStatuses")}</option>
                      <option value="READY">{t("status.migration.READY")}</option>
                      <option value="REVIEW_REQUIRED">
                        {t("status.migration.REVIEW_REQUIRED")}
                      </option>
                      <option value="BLOCKED">{t("status.migration.BLOCKED")}</option>
                    </select>
                  </label>
                  <label className="select-field">
                    <select
                      value={typeFilter}
                      onChange={(event) => {
                        setTypeFilter(event.target.value);
                        setPage(0);
                      }}
                      aria-label={t("migration.filters.type")}
                    >
                      <option value="">{t("migration.filters.allTypes")}</option>
                      {ITEM_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {t(`migration.itemTypes.${type}`)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="select-field">
                    <select
                      value={scopeFilter}
                      onChange={(event) => {
                        setScopeFilter(event.target.value);
                        setPage(0);
                      }}
                      aria-label={t("migration.filters.scope")}
                    >
                      <option value="">{t("migration.filters.allItems")}</option>
                      <option value="included">{t("migration.included")}</option>
                      <option value="excluded">{t("migration.excluded")}</option>
                    </select>
                  </label>
                  {queryText || statusFilter || typeFilter || scopeFilter ? (
                    <button className="button button-ghost button-small" onClick={resetFilters}>
                      {t("migration.filters.reset")}
                    </button>
                  ) : null}
                </div>
                {batchIds.size > 0 ? (
                  <div className="migration-batch-bar">
                    <span className="migration-count">
                      {t("migration.batch.selected", { count: batchIds.size })}
                    </span>
                    <button
                      type="button"
                      className="button button-secondary button-small"
                      disabled={batchPreviewMutation.isPending || batchApplyMutation.isPending}
                      onClick={() => batchPreviewMutation.mutate()}
                    >
                      <Layers size={14} />
                      {t("migration.batch.preview")}
                    </button>
                    <button
                      type="button"
                      className="button button-primary button-small"
                      disabled={batchApplyMutation.isPending || batchPreviewMutation.isPending}
                      onClick={() => {
                        setBatchError(null);
                        setBatchConfirm(false);
                        setBatchApplyOpen(true);
                      }}
                    >
                      <Play size={14} />
                      {t("migration.batch.apply")}
                    </button>
                    <button
                      type="button"
                      className="button button-ghost button-small"
                      onClick={clearBatch}
                    >
                      {t("migration.batch.clear")}
                    </button>
                  </div>
                ) : null}
                {batchError ? <p className="inline-error">{batchError.message}</p> : null}
                {appliedRunIds.length > 0 ? (
                  <div className="migration-batch-bar">
                    <span className="migration-count">
                      {t("migration.batch.appliedBatch", {
                        count: appliedRunIds.length
                      })}
                    </span>
                    <button
                      type="button"
                      className="button button-secondary button-small"
                      onClick={() => {
                        setBatchRestoreConfirm(false);
                        setBatchResultOpen(true);
                      }}
                    >
                      <RotateCcw size={14} />
                      {t("migration.batch.restore")}
                    </button>
                  </div>
                ) : null}
                {itemsQuery.isLoading ? <LoadingBlock /> : null}
                {itemsQuery.error ? (
                  <ErrorPanel
                    error={itemsQuery.error}
                    onRetry={() => itemsQuery.refetch()}
                  />
                ) : null}
                {itemsQuery.data && itemsQuery.data.content.length === 0 ? (
                  <EmptyState
                    icon={ListChecks}
                    title={t("migration.noItemsTitle")}
                    description={t("migration.noItemsDescription")}
                  />
                ) : null}
                {itemsQuery.data && itemsQuery.data.content.length > 0 ? (
                  <div className="migration-item-list">
                    {itemsQuery.data.content.map((item) => (
                      <ItemRow
                        key={item.id}
                        item={item}
                        pending={scopeMutation.isPending}
                        batchEnabled
                        selectedBatch={batchIds.has(item.id)}
                        onSelect={(itemId) => setSelectedItemId(itemId)}
                        onToggle={(itemId, included) =>
                          scopeMutation.mutate({ itemId, included })
                        }
                        onToggleBatch={toggleBatch}
                      />
                    ))}
                  </div>
                ) : null}
                {scopeError ? <p className="inline-error">{scopeError}</p> : null}
                {itemsQuery.data && itemsQuery.data.totalPages > 1 ? (
                  <footer className="pagination">
                    <button
                      className="button button-ghost button-small"
                      disabled={page === 0}
                      onClick={() => setPage(Math.max(0, page - 1))}
                    >
                      {t("common.previous")}
                    </button>
                    <span>
                      {t("common.pageOf", {
                        page: page + 1,
                        total: itemsQuery.data.totalPages
                      })}
                    </span>
                    <button
                      className="button button-ghost button-small"
                      disabled={page >= itemsQuery.data.totalPages - 1}
                      onClick={() => setPage(page + 1)}
                    >
                      {t("common.next")}
                    </button>
                  </footer>
                ) : null}
              </section>
            ) : null}

            {view === "phases" ? (
              <section className="surface migration-phases-panel">
                <header className="section-header">
                  <div>
                    <span className="eyebrow">{t("migration.phasesEyebrow")}</span>
                    <h2>{t("migration.phasesTitle")}</h2>
                    <p>{t("migration.phasesHint")}</p>
                  </div>
                </header>
                {phaseItemsQuery.isLoading ? <LoadingBlock /> : null}
                {phaseItemsQuery.error ? (
                  <ErrorPanel
                    error={phaseItemsQuery.error}
                    onRetry={() => phaseItemsQuery.refetch()}
                  />
                ) : null}
                {phaseItemsQuery.data && phaseItemsQuery.data.totalElements > 200 ? (
                  <p className="migration-note">{t("migration.phasesTruncated")}</p>
                ) : null}
                <div className="migration-phases">
                  {(plan?.phases || []).map((phase) => {
                    const phaseItems =
                      phaseItemsQuery.data?.content.filter(
                        (item) => item.phase === phase.phase
                      ) || [];
                    return (
                      <section className="migration-phase" key={phase.phase}>
                        <header>
                          <span className="migration-phase-index">
                            {t("migration.phaseLabel", { value: phase.phase })}
                          </span>
                          <span className="migration-count">
                            {t("migration.itemCount", { count: phase.itemCount })}
                          </span>
                        </header>
                        <div className="migration-phase-status">
                          <span>{t("status.migration.READY")} {phase.readyCount}</span>
                          <span>{t("status.migration.REVIEW_REQUIRED")} {phase.reviewCount}</span>
                          <span>{t("status.migration.BLOCKED")} {phase.blockedCount}</span>
                        </div>
                        {phaseItems.length === 0 ? (
                          <p className="migration-muted">
                            {t("migration.phaseEmpty")}
                          </p>
                        ) : (
                          <div className="migration-phase-items">
                            {phaseItems.map((item) => (
                              <button
                                key={item.id}
                                className="migration-phase-item"
                                onClick={() => setSelectedItemId(item.id)}
                              >
                                <span>
                                  <strong>{item.title}</strong>
                                  <small>
                                    {t(`migration.itemTypes.${item.itemType}`, {
                                      defaultValue: item.itemType
                                    })}
                                  </small>
                                </span>
                                <StatusBadge value={item.status} kind="migration" />
                                <ChevronRight size={14} />
                              </button>
                            ))}
                          </div>
                        )}
                      </section>
                    );
                  })}
                </div>
                {plan?.phases.length === 0 ? (
                  <EmptyState
                    icon={GitBranch}
                    title={t("migration.phasesEmptyTitle")}
                    description={t("migration.phasesEmptyDescription")}
                  />
                ) : null}
              </section>
            ) : null}

            {view === "dependencies" ? (
              <section className="surface migration-graph-panel">
                <header className="section-header">
                  <div>
                    <span className="eyebrow">{t("migration.dependenciesEyebrow")}</span>
                    <h2>{t("migration.graphTitle")}</h2>
                    <p>{t("migration.graphHint")}</p>
                  </div>
                </header>
                {graphQuery.isLoading ? <LoadingBlock /> : null}
                {graphQuery.error ? (
                  <ErrorPanel
                    error={graphQuery.error}
                    onRetry={() => graphQuery.refetch()}
                  />
                ) : null}
                {graphQuery.data ? (
                  <MigrationGraphView
                    graph={graphQuery.data as MigrationGraph}
                    onSelectItem={setSelectedItemId}
                  />
                ) : null}
              </section>
            ) : null}

            {view === "review" ? (
              <section className="surface migration-list">
                <header className="section-header">
                  <div>
                    <span className="eyebrow">{t("migration.reviewEyebrow")}</span>
                    <h2>{t("migration.reviewTitle")}</h2>
                    <p>{t("migration.reviewHint")}</p>
                  </div>
                  <span className="migration-count">
                    {reviewQuery.data
                      ? t("migration.itemCount", {
                          count: reviewQuery.data.totalElements
                        })
                      : null}
                  </span>
                </header>
                {reviewQuery.isLoading ? <LoadingBlock /> : null}
                {reviewQuery.error ? (
                  <ErrorPanel
                    error={reviewQuery.error}
                    onRetry={() => reviewQuery.refetch()}
                  />
                ) : null}
                {reviewQuery.data && reviewQuery.data.content.length === 0 ? (
                  <EmptyState
                    icon={ShieldAlert}
                    title={t("migration.reviewEmptyTitle")}
                    description={t("migration.reviewEmptyDescription")}
                  />
                ) : null}
                {reviewQuery.data && reviewQuery.data.content.length > 0 ? (
                  <div className="migration-item-list">
                    {reviewQuery.data.content.map((item) => (
                      <ItemRow
                        key={item.id}
                        item={item}
                        pending={scopeMutation.isPending}
                        batchEnabled={false}
                        selectedBatch={batchIds.has(item.id)}
                        onSelect={(itemId) => setSelectedItemId(itemId)}
                        onToggle={(itemId, included) =>
                          scopeMutation.mutate({ itemId, included })
                        }
                        onToggleBatch={toggleBatch}
                      />
                    ))}
                  </div>
                ) : null}
                {scopeError ? <p className="inline-error">{scopeError}</p> : null}
              </section>
            ) : null}

            {view === "history" ? (
              <section className="surface migration-history">
                <header className="section-header">
                  <div>
                    <span className="eyebrow">{t("migration.historyEyebrow")}</span>
                    <h2>{t("migration.historyTitle")}</h2>
                    <p>{t("migration.historyHint")}</p>
                  </div>
                </header>
                <div className="migration-history-list">
                  {plans.map((planSummary) => (
                    <PlanRow
                      key={planSummary.id}
                      plan={planSummary}
                      current={planSummary.id === summary.id}
                      onOpen={() => selectPlan(planSummary.id)}
                    />
                  ))}
                </div>
              </section>
            ) : null}
          </div>
        </>
      )}

      {selectedItemId && selectedPlanId ? (
        <MigrationInspector
          projectId={projectId}
          planId={selectedPlanId}
          itemId={selectedItemId}
          onClose={() => setSelectedItemId(null)}
          onSelectItem={setSelectedItemId}
          onToggleScope={(itemId, included) =>
            scopeMutation.mutate({ itemId, included })
          }
          scopePending={scopeMutation.isPending}
          scopeError={scopeError}
        />
      ) : null}

      <Modal
        open={generateOpen}
        title={t("migration.generateTitle")}
        description={t("migration.generateDescription")}
        onClose={() => {
          setGenerateOpen(false);
          generateMutation.reset();
        }}
      >
        <div className="migration-generate-body">
          <label className="field">
            <span>{t("migration.targetProfile")}</span>
            <select
              value={targetProfile}
              onChange={(event) => setTargetProfile(event.target.value)}
              aria-label={t("migration.targetProfile")}
            >
              {TARGET_PROFILES.map((profile) => (
                <option key={profile} value={profile}>
                  {t(`migration.profiles.${profile}`, { defaultValue: profile })}
                </option>
              ))}
            </select>
          </label>
          <p className="migration-note">{t("migration.targetProfileHint")}</p>
          <p className="migration-note">
            {latestCompleted
              ? t("migration.usesScan", { number: latestCompleted.sequence })
              : t("migration.noCompletedScanDescription")}
          </p>
          {generateMutation.error ? (
            <p className="inline-error">{generateMutation.error.message}</p>
          ) : null}
          <div className="modal-actions">
            <button
              className="button button-ghost"
              onClick={() => setGenerateOpen(false)}
            >
              {t("common.cancel")}
            </button>
            <button
              className="button button-primary"
              disabled={!hasCompletedScan || generateMutation.isPending}
              onClick={() => generateMutation.mutate()}
            >
              <Sparkles size={15} />
              {generateMutation.isPending
                ? t("migration.generating")
                : t("migration.generate")}
            </button>
          </div>
        </div>
      </Modal>
      <Modal
        open={batchApplyOpen}
        title={t("migration.batch.confirmTitle")}
        description={t("migration.batch.confirmDescription", {
          count: batchIds.size
        })}
        onClose={() => {
          setBatchApplyOpen(false);
          setBatchConfirm(false);
          setBatchError(null);
        }}
      >
        <div className="migration-generate-body">
          <label className="migration-confirm">
            <input
              type="checkbox"
              checked={batchConfirm}
              onChange={(event) => setBatchConfirm(event.target.checked)}
            />
            <span>{t("migration.batch.confirm")}</span>
          </label>
          {batchError ? <p className="inline-error">{batchError.message}</p> : null}
          <div className="modal-actions">
            <button
              className="button button-ghost"
              onClick={() => {
                setBatchApplyOpen(false);
                setBatchConfirm(false);
              }}
            >
              {t("common.cancel")}
            </button>
            <button
              className="button button-primary"
              disabled={!batchConfirm || batchApplyMutation.isPending}
              onClick={() => batchApplyMutation.mutate()}
            >
              <Play size={15} />
              {batchApplyMutation.isPending
                ? t("migration.batch.working")
                : t("migration.batch.apply")}
            </button>
          </div>
        </div>
      </Modal>

      <Modal
        open={batchResultOpen}
        title={t("migration.batch.resultTitle")}
        onClose={() => {
          setBatchResultOpen(false);
          setBatchError(null);
        }}
      >
        {batchResult ? (
          <div className="migration-batch-result">
            <div className="migration-batch-summary">
              <span className="migration-chip">
                {t(`migration.batch.statuses.${batchResult.status}`, {
                  defaultValue: batchResult.status
                })}
              </span>
              <span>
                {t("migration.batch.requested")}: {batchResult.requested}
              </span>
              <span>
                {t("migration.batch.applied")}: {batchResult.applied}
              </span>
              {batchResult.restored > 0 ? (
                <span>
                  {t("migration.batch.restored")}: {batchResult.restored}
                </span>
              ) : null}
              {batchResult.restoredOnFailure > 0 ? (
                <span>
                  {t("migration.batch.rolledBack")}: {batchResult.restoredOnFailure}
                </span>
              ) : null}
              {batchResult.failed > 0 ? (
                <span>
                  {t("migration.batch.failed")}: {batchResult.failed}
                </span>
              ) : null}
              {batchResult.items.filter((item) => item.status === "NOT_ATTEMPTED").length > 0 ? (
                <span>
                  {t("migration.batch.notAttempted")}:{" "}
                  {batchResult.items.filter((item) => item.status === "NOT_ATTEMPTED").length}
                </span>
              ) : null}
            </div>
            <p className="migration-note">{t("migration.batch.previewNote")}</p>
            <div className="migration-batch-items">
              {batchResult.items.map((item, index) => (
                <div className="migration-batch-item" key={`${item.itemId ?? item.runId ?? index}`}>
                  <div>
                    <strong>{item.title || item.itemId || item.runId}</strong>
                    <small>
                      {item.proposal?.ruleId ?? item.run?.ruleId ?? item.errorCode ?? ""}
                    </small>
                  </div>
                  <span className="migration-chip">
                    {t(`migration.batch.statuses.${item.status}`, {
                      defaultValue: item.status
                    })}
                  </span>
                  {item.errorMessage ? (
                    <p className="inline-error">{item.errorMessage}</p>
                  ) : null}
                </div>
              ))}
            </div>
            {appliedRunIds.length > 0 ? (
              <div className="migration-batch-restore">
                <label className="migration-confirm">
                  <input
                    type="checkbox"
                    checked={batchRestoreConfirm}
                    onChange={(event) => setBatchRestoreConfirm(event.target.checked)}
                  />
                  <span>{t("migration.batch.restoreConfirm")}</span>
                </label>
                <button
                  className="button button-secondary"
                  disabled={!batchRestoreConfirm || batchRestoreMutation.isPending}
                  onClick={() => batchRestoreMutation.mutate(appliedRunIds)}
                >
                  <RotateCcw size={15} />
                  {batchRestoreMutation.isPending
                    ? t("migration.batch.restoreWorking")
                    : t("migration.batch.restore")}
                </button>
              </div>
            ) : null}
            {batchError ? <p className="inline-error">{batchError.message}</p> : null}
            <div className="modal-actions">
              <button
                className="button button-ghost"
                onClick={() => {
                  setBatchResultOpen(false);
                  setBatchError(null);
                }}
              >
                {t("migration.batch.close")}
              </button>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}

function Metric({
  label,
  value,
  tone = "neutral"
}: {
  label: string;
  value: number;
  tone?: "neutral" | "success" | "warning" | "danger";
}) {
  const { i18n } = useTranslation();
  return (
    <div className={`migration-metric migration-metric-${tone}`}>
      <strong>{formatNumber(value, i18n.language)}</strong>
      <span>{label}</span>
    </div>
  );
}

function ItemRow({
  item,
  pending,
  batchEnabled,
  selectedBatch,
  onSelect,
  onToggle,
  onToggleBatch
}: {
  item: MigrationItem;
  pending: boolean;
  batchEnabled: boolean;
  selectedBatch: boolean;
  onSelect: (itemId: string) => void;
  onToggle: (itemId: string, included: boolean) => void;
  onToggleBatch: (itemId: string, selected: boolean) => void;
}) {
  const { t } = useTranslation();
  return (
    <article
      className={`migration-row ${item.included ? "" : "migration-row-excluded"} ${
        batchEnabled ? "" : "migration-row-no-batch"
      }`}
      data-item-id={item.id}
    >
      {batchEnabled ? (
        <label className="migration-batch-check">
          <input
            type="checkbox"
            checked={selectedBatch}
            disabled={!item.included}
            onChange={(event) => onToggleBatch(item.id, event.target.checked)}
            aria-label={t("migration.batch.selectLabel", { title: item.title })}
          />
        </label>
      ) : null}
      <button className="migration-row-open" onClick={() => onSelect(item.id)}>
        <span className="migration-row-identity">
          <span className="migration-row-title">
            <strong>{item.title}</strong>
            {item.primaryFilePath && item.primaryFilePath !== item.title ? (
              <small>{item.primaryFilePath}</small>
            ) : (
              <small>{item.itemKey}</small>
            )}
          </span>
          <span className="migration-row-badges">
            <StatusBadge value={item.status} kind="migration" />
            <span className="migration-chip">
              {t(`migration.itemTypes.${item.itemType}`, {
                defaultValue: item.itemType
              })}
            </span>
            <span className="migration-chip">
              {t(`migration.targets.${item.targetKey}`, {
                defaultValue: item.targetKey
              })}
            </span>
          </span>
        </span>
        <span className="migration-row-facts">
          <span>{t("migration.phaseLabel", { value: item.phase })}</span>
          <span>{t("migration.priorityLabel", { value: item.priority })}</span>
          <span>
            {t(`migration.risks.${item.riskLevel}`, {
              defaultValue: item.riskLevel
            })}
          </span>
          <span>
            {t(`migration.automations.${item.automationLevel}`, {
              defaultValue: item.automationLevel
            })}
          </span>
          <span>{t("migration.evidenceCount", { count: item.findingCount })}</span>
          <span>{t("migration.dependencyCount", { count: item.dependencyCount })}</span>
        </span>
        <ChevronRight size={15} className="migration-row-arrow" />
      </button>
      <label className="migration-toggle">
        <input
          type="checkbox"
          checked={item.included}
          disabled={pending}
          onChange={(event) => onToggle(item.id, event.target.checked)}
          aria-label={t("migration.scopeToggleLabel", { title: item.title })}
        />
        <span>{item.included ? t("migration.included") : t("migration.excluded")}</span>
      </label>
    </article>
  );
}

function PlanRow({
  plan,
  current,
  onOpen
}: {
  plan: MigrationPlanSummary;
  current: boolean;
  onOpen: () => void;
}) {
  const { t, i18n } = useTranslation();
  return (
    <article className={`migration-history-row ${current ? "migration-history-current" : ""}`}>
      <div>
        <strong>{t("migration.planNumber", { number: plan.sequence })}</strong>
        <small>
          {t("migration.sourceScan", { number: plan.sourceScanSequence })} ·{" "}
          {formatDate(plan.createdAt, i18n.language)}
        </small>
      </div>
      <div className="migration-history-counts">
        <span>{t("migration.itemCount", { count: plan.includedCount })}</span>
        <span>{t("migration.reviewCount", { count: plan.reviewCount })}</span>
        <span>{t("migration.blockedCount", { count: plan.blockedCount })}</span>
      </div>
      <div className="migration-history-actions">
        {current ? (
          <span className="migration-current-tag">{t("migration.current")}</span>
        ) : null}
        {plan.stale ? (
          <span className="migration-chip migration-chip-warning">
            {t("migration.stale")}
          </span>
        ) : null}
        <button className="button button-ghost button-small" onClick={onOpen}>
          {t("migration.openPlan")}
          <ArrowRight size={14} />
        </button>
      </div>
    </article>
  );
}
