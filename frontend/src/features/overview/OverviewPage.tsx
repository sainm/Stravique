import { useQuery } from "@tanstack/react-query";
import {
  AlertCircle,
  ArrowRight,
  Boxes,
  CheckCircle2,
  Clock3,
  Code2,
  FileWarning,
  FolderTree,
  GitCompareArrows,
  Layers3,
  Play,
  RefreshCw,
  ScanSearch,
  ShieldCheck,
  Sparkles,
  TriangleAlert
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import EmptyState from "../../components/EmptyState";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import PageHeader from "../../components/PageHeader";
import ScanButton from "../../components/ScanButton";
import StatusBadge from "../../components/StatusBadge";
import { api } from "../../lib/api";
import {
  formatBytes,
  formatDate,
  formatDuration,
  formatNumber
} from "../../lib/format";

const activeStatuses = ["QUEUED", "RUNNING", "CANCEL_REQUESTED"];

export default function OverviewPage() {
  const { projectId = "" } = useParams();
  const { t, i18n } = useTranslation();
  const project = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => api.project(projectId)
  });
  const overview = useQuery({
    queryKey: ["overview", projectId],
    queryFn: () => api.overview(projectId),
    refetchInterval: (query) =>
      activeStatuses.includes(query.state.data?.currentScan?.status || "")
        ? 800
        : false
  });

  if (project.isLoading || overview.isLoading) {
    return <LoadingBlock />;
  }
  if (project.error) {
    return <ErrorPanel error={project.error} onRetry={() => project.refetch()} />;
  }
  if (overview.error) {
    return <ErrorPanel error={overview.error} onRetry={() => overview.refetch()} />;
  }
  if (!project.data || !overview.data) {
    return null;
  }

  const data = overview.data;
  const scan = data.currentScan;
  const active = activeStatuses.includes(scan?.status || "");
  const scanCompleted = scan?.status === "COMPLETED";
  const actionCopy = t(`overview.nextActions.${data.nextAction}`);
  const actionTarget = actionFor(data.nextAction, projectId, scan?.status);

  return (
    <>
      <PageHeader
        eyebrow={t("overview.eyebrow")}
        title={project.data.name}
        description={
          project.data.description || t("overview.defaultDescription")
        }
        actions={
          <div className="header-action-group">
            <button
              className="button button-ghost"
              onClick={() => overview.refetch()}
              aria-label={t("common.refresh")}
            >
              <RefreshCw size={15} />
              {t("common.refresh")}
            </button>
            <ScanButton projectId={projectId} active={active} />
          </div>
        }
      />

      {!scan ? (
        <section className="surface no-scan-panel">
          <EmptyState
            icon={ScanSearch}
            title={t("overview.noScanTitle")}
            description={t("overview.noScanDescription")}
            action={<ScanButton projectId={projectId} label={t("overview.runFirstScan")} />}
          />
          <div className="scan-facts">
            <div>
              <ShieldCheck size={16} />
              <span>{t("overview.facts.readOnly")}</span>
            </div>
            <div>
              <FolderTree size={16} />
              <span>{t("overview.facts.structure")}</span>
            </div>
            <div>
              <GitCompareArrows size={16} />
              <span>{t("overview.facts.changeTracking")}</span>
            </div>
          </div>
        </section>
      ) : (
        <>
          <section
            className={`scan-state-panel scan-state-${scan.status.toLowerCase()}`}
          >
            <div className="scan-state-identity">
              <span className="scan-sequence">
                {t("overview.scanNumber", { number: scan.sequence })}
              </span>
              <StatusBadge value={scan.status} kind="scan" />
            </div>
            <div className="scan-state-meta">
              <span>
                <Clock3 size={14} />
                {scan.startedAt
                  ? formatDate(scan.startedAt, i18n.language)
                  : formatDate(scan.createdAt, i18n.language)}
              </span>
              {scan.finishedAt ? (
                <span>{formatDuration(scan.startedAt, scan.finishedAt)}</span>
              ) : null}
              <span>v{scan.analyzerVersion}</span>
            </div>
            {active ? (
              <div className="scan-active-copy">
                <RefreshCw className="spin" size={17} />
                <span>{t("overview.scanInProgress")}</span>
              </div>
            ) : null}
            {scan.status === "FAILED" ? (
              <div className="scan-error-copy">
                <AlertCircle size={16} />
                <span>{scan.errorMessage || scan.errorCode}</span>
              </div>
            ) : null}
          </section>

          <section className="metric-strip" aria-label={t("overview.metricsLabel")}>
            <Metric
              label={t("overview.metrics.files")}
              value={formatNumber(data.totalFiles, i18n.language)}
              icon={Code2}
            />
            <Metric
              label={t("overview.metrics.modules")}
              value={formatNumber(data.moduleCount, i18n.language)}
              icon={Layers3}
            />
            <Metric
              label={t("overview.metrics.analyzed")}
              value={formatNumber(data.analyzedFiles, i18n.language)}
              icon={CheckCircle2}
            />
            <Metric
              label={t("overview.metrics.evidence")}
              value={formatNumber(scan.findingCount, i18n.language)}
              icon={Sparkles}
            />
            <Metric
              label={t("overview.metrics.review")}
              value={formatNumber(data.reviewItems, i18n.language)}
              icon={TriangleAlert}
              tone={data.reviewItems > 0 ? "warning" : "neutral"}
            />
            <Metric
              label={t("overview.metrics.parseFailures")}
              value={formatNumber(data.parseFailures, i18n.language)}
              icon={FileWarning}
              tone={data.parseFailures > 0 ? "danger" : "neutral"}
            />
          </section>

          <section className="next-action">
            <div className="next-action-icon">
              {scanCompleted ? <ArrowRight size={19} /> : <AlertCircle size={19} />}
            </div>
            <div>
              <span className="eyebrow">{t("overview.nextActionEyebrow")}</span>
              <strong>{actionCopy}</strong>
            </div>
            <Link className="button button-quiet button-small" to={actionTarget}>
              {t("common.continue")}
              <ArrowRight size={14} />
            </Link>
          </section>

          <div className="overview-grid">
            <section className="surface inventory-insight">
              <header className="section-header">
                <div>
                  <span className="eyebrow">{t("overview.stackEyebrow")}</span>
                  <h2>{t("overview.stackTitle")}</h2>
                </div>
                <Link to={`/projects/${projectId}/frameworks`} className="text-link">
                  {t("overview.openFrameworks")}
                  <ArrowRight size={14} />
                </Link>
              </header>
              {data.technologies.length === 0 ? (
                <div className="section-empty">{t("overview.noTechnology")}</div>
              ) : (
                <div className="technology-table">
                  {data.technologies.slice(0, 8).map((technology) => (
                    <Link
                      className="technology-row"
                      key={technology.id}
                      to={`/projects/${projectId}/frameworks?key=${technology.technologyKey}`}
                    >
                      <div className="technology-name">
                        <span className="technology-glyph">
                          <Boxes size={15} />
                        </span>
                        <div>
                          <strong>{technology.name}</strong>
                          <small>
                            {t(`technology.category.${technology.category}`, {
                              defaultValue: technology.category
                            })}
                          </small>
                        </div>
                      </div>
                      <StatusBadge value={technology.status} kind="certainty" />
                      <span className="evidence-count">
                        {t("common.evidenceCount", {
                          count: technology.totalEvidenceCount
                        })}
                      </span>
                      <ArrowRight size={14} />
                    </Link>
                  ))}
                </div>
              )}
              {data.technologies.length > 8 ? (
                <Link
                  to={`/projects/${projectId}/frameworks`}
                  className="section-more"
                >
                  {t("overview.moreTechnologies", {
                    count: data.technologies.length - 8
                  })}
                </Link>
              ) : null}
            </section>

            <section className="surface review-insight">
              <header className="section-header">
                <div>
                  <span className="eyebrow">{t("overview.reviewEyebrow")}</span>
                  <h2>{t("overview.reviewTitle")}</h2>
                </div>
                <span className="section-count">
                  {formatNumber(data.reviewItems, i18n.language)}
                </span>
              </header>
              {data.reviewHighlights.length === 0 ? (
                <div className="review-clear">
                  <CheckCircle2 size={20} />
                  <div>
                    <strong>{t("overview.noReviewTitle")}</strong>
                    <p>{t("overview.noReviewDescription")}</p>
                  </div>
                </div>
              ) : (
                <div className="review-list">
                  {data.reviewHighlights.map((finding) => (
                    <Link
                      key={finding.id}
                      to={`/projects/${projectId}/inventory?fileId=${finding.fileId || ""}`}
                      className="review-item"
                    >
                      <StatusBadge value={finding.certainty} kind="certainty" />
                      <strong>{finding.summary}</strong>
                      <code>
                        {finding.filePath}
                        {finding.lineStart ? `:${finding.lineStart}` : ""}
                      </code>
                    </Link>
                  ))}
                </div>
              )}
              {data.reviewItems > data.reviewHighlights.length ? (
                <Link
                  to={`/projects/${projectId}/inventory?review=true`}
                  className="section-more"
                >
                  {t("overview.openAllReviews")}
                  <ArrowRight size={14} />
                </Link>
              ) : null}
            </section>
          </div>

          <div className="overview-grid overview-grid-lower">
            <section className="surface composition-panel">
              <header className="section-header">
                <div>
                  <span className="eyebrow">{t("overview.compositionEyebrow")}</span>
                  <h2>{t("overview.compositionTitle")}</h2>
                </div>
                <Link to={`/projects/${projectId}/inventory`} className="text-link">
                  {t("overview.openExplorer")}
                  <ArrowRight size={14} />
                </Link>
              </header>
              <FileComposition values={data.fileTypes} total={data.totalFiles} />
            </section>

            <section className="surface module-panel">
              <header className="section-header">
                <div>
                  <span className="eyebrow">{t("overview.structureEyebrow")}</span>
                  <h2>{t("overview.structureTitle")}</h2>
                </div>
              </header>
              <div className="module-list">
                {data.modules.map((module) => {
                  const dependencies = data.moduleDependencies.filter(
                    (dependency) => dependency.fromModulePath === module.relativePath
                  );
                  return (
                    <div className="module-row" key={module.id}>
                      <FolderTree size={16} />
                      <div>
                        <strong>{module.name}</strong>
                        <code>{module.relativePath || "."}</code>
                        {dependencies.map((dependency) => (
                          <span
                            className="module-dependency"
                            key={dependency.id}
                            title={`${dependency.evidencePath}:${dependency.lineStart || ""}`}
                          >
                            {t("overview.moduleDependency", {
                              target: dependency.toModulePath
                            })}
                          </span>
                        ))}
                      </div>
                      <span>
                        {t("overview.rootCount", {
                          count:
                            module.sourceRoots.length +
                            module.webRoots.length +
                            module.resourceRoots.length
                        })}
                      </span>
                    </div>
                  );
                })}
              </div>
            </section>
          </div>

          <section className="surface scan-history">
            <header className="section-header">
              <div>
                <span className="eyebrow">{t("overview.historyEyebrow")}</span>
                <h2>{t("overview.historyTitle")}</h2>
              </div>
              <span className="section-note">{t("overview.historyNote")}</span>
            </header>
            <div className="history-list">
              {data.recentScans.map((item) => (
                <div className="history-row" key={item.id}>
                  <span className="history-sequence">#{item.sequence}</span>
                  <StatusBadge value={item.status} kind="scan" />
                  <span className="history-date">
                    {formatDate(item.finishedAt || item.createdAt, i18n.language)}
                  </span>
                  <span>
                    {t("overview.historyFiles", {
                      count: item.fileCount
                    })}
                  </span>
                  <span>
                    {item.finishedAt
                      ? formatDuration(item.startedAt, item.finishedAt)
                      : "—"}
                  </span>
                  {item.status === "COMPLETED" ? (
                    <span className="history-review">
                      {t("overview.historyReviews", { count: item.reviewCount })}
                    </span>
                  ) : (
                    <span />
                  )}
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </>
  );
}

function Metric({
  label,
  value,
  icon: Icon,
  tone = "neutral"
}: {
  label: string;
  value: string;
  icon: typeof Play;
  tone?: "neutral" | "warning" | "danger";
}) {
  return (
    <div className={`metric metric-${tone}`}>
      <Icon size={16} strokeWidth={1.8} />
      <div>
        <strong>{value}</strong>
        <span>{label}</span>
      </div>
    </div>
  );
}

function FileComposition({
  values,
  total
}: {
  values: Record<string, number>;
  total: number;
}) {
  const { t } = useTranslation();
  const entries = Object.entries(values).sort((left, right) => right[1] - left[1]);
  const colors = [
    "#6650d9",
    "#8d7bea",
    "#3b8d78",
    "#b2782c",
    "#7b8794",
    "#b85b68",
    "#556f96",
    "#9b7fbc"
  ];
  return (
    <div className="file-composition">
      <div className="composition-bar" aria-hidden="true">
        {entries.map(([type, count], index) => (
          <span
            key={type}
            style={{
              width: `${(count / Math.max(1, total)) * 100}%`,
              background: colors[index % colors.length]
            }}
            title={`${type}: ${count}`}
          />
        ))}
      </div>
      <div className="composition-legend">
        {entries.map(([type, count], index) => (
          <div key={type}>
            <span style={{ background: colors[index % colors.length] }} />
            <strong>{t(`fileType.${type}`, { defaultValue: type })}</strong>
            <small>{count}</small>
          </div>
        ))}
      </div>
    </div>
  );
}

function actionFor(nextAction: string, projectId: string, status?: string): string {
  if (nextAction === "RUN_SCAN" || nextAction === "RETRY_SCAN") {
    return `/projects/${projectId}/overview`;
  }
  if (nextAction === "REVIEW_PARSE_FAILURES" || nextAction === "REVIEW_UNCERTAINTIES") {
    return `/projects/${projectId}/inventory?review=true`;
  }
  if (nextAction === "WAIT_FOR_SCAN" && status === "RUNNING") {
    return `/projects/${projectId}/overview`;
  }
  return `/projects/${projectId}/inventory`;
}
