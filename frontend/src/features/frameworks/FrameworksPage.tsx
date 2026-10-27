import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  Boxes,
  Braces,
  ChevronRight,
  CircleHelp,
  CodeXml,
  Database,
  FileCode2,
  FlaskConical,
  Layers3,
  RefreshCw,
  Wrench
} from "lucide-react";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams, useSearchParams } from "react-router-dom";
import EmptyState from "../../components/EmptyState";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import PageHeader from "../../components/PageHeader";
import ScanButton from "../../components/ScanButton";
import StatusBadge from "../../components/StatusBadge";
import { api } from "../../lib/api";
import { formatDate, formatNumber } from "../../lib/format";

const categoryIcons = {
  LANGUAGE: Braces,
  BUILD: Wrench,
  FRAMEWORK: Layers3,
  WEB: CodeXml,
  TEST: FlaskConical,
  DATABASE: Database
};

export default function FrameworksPage() {
  const { projectId = "" } = useParams();
  const { t, i18n } = useTranslation();
  const [params, setParams] = useSearchParams();
  const selectedKey = params.get("key") || "";
  const project = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => api.project(projectId)
  });
  const technologies = useQuery({
    queryKey: ["frameworks", projectId],
    queryFn: () => api.frameworks(projectId)
  });

  useEffect(() => {
    if (!selectedKey && technologies.data?.length) {
      const preferred =
        technologies.data.find((item) => item.category === "FRAMEWORK") ||
        technologies.data[0];
      setParams({ key: preferred.technologyKey }, { replace: true });
    }
  }, [selectedKey, setParams, technologies.data]);

  const detail = useQuery({
    queryKey: ["framework", projectId, selectedKey],
    queryFn: () => api.framework(projectId, selectedKey),
    enabled: Boolean(selectedKey)
  });
  const activeScan = ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(
    project.data?.latestScan?.status || ""
  );

  return (
    <>
      <PageHeader
        eyebrow={t("frameworks.eyebrow")}
        title={t("frameworks.title")}
        description={t("frameworks.description")}
        actions={
          <div className="header-action-group">
            <button
              className="button button-ghost button-small"
              onClick={() => technologies.refetch()}
            >
              <RefreshCw size={14} />
              {t("common.refresh")}
            </button>
            <ScanButton projectId={projectId} active={activeScan} compact />
          </div>
        }
      />

      {!project.data?.latestScan && !project.isLoading ? (
        <section className="surface">
          <EmptyState
            icon={Boxes}
            title={t("frameworks.noScanTitle")}
            description={t("frameworks.noScanDescription")}
            action={<ScanButton projectId={projectId} />}
          />
        </section>
      ) : null}
      {technologies.isLoading ? <LoadingBlock /> : null}
      {technologies.error ? (
        <ErrorPanel
          error={technologies.error}
          onRetry={() => technologies.refetch()}
        />
      ) : null}

      {project.data?.latestScan && technologies.data?.length === 0 ? (
        <section className="surface">
          <EmptyState
            icon={Boxes}
            title={t("frameworks.emptyTitle")}
            description={t("frameworks.emptyDescription")}
          />
        </section>
      ) : null}

      {technologies.data && technologies.data.length > 0 ? (
        <div className="framework-workspace">
          <aside className="framework-index surface">
            <header className="explorer-pane-header">
              <div>
                <span className="eyebrow">{t("frameworks.indexEyebrow")}</span>
                <h2>{t("frameworks.indexTitle")}</h2>
              </div>
              <span>{technologies.data.length}</span>
            </header>
            <div className="framework-list">
              {technologies.data.map((technology) => {
                const Icon =
                  categoryIcons[
                    technology.category as keyof typeof categoryIcons
                  ] || Boxes;
                return (
                  <button
                    key={technology.id}
                    className={`framework-row ${
                      technology.technologyKey === selectedKey
                        ? "framework-row-active"
                        : ""
                    }`}
                    onClick={() => setParams({ key: technology.technologyKey })}
                  >
                    <span className="framework-row-icon">
                      <Icon size={16} />
                    </span>
                    <span className="framework-row-copy">
                      <strong>{technology.name}</strong>
                      <small>
                        {t(`technology.category.${technology.category}`, {
                          defaultValue: technology.category
                        })}
                      </small>
                    </span>
                    <StatusBadge value={technology.status} kind="certainty" />
                    <ChevronRight size={14} />
                  </button>
                );
              })}
            </div>
          </aside>

          <main className="framework-detail">{renderDetail()}</main>
        </div>
      ) : null}
    </>
  );

  function renderDetail() {
    if (detail.isLoading) {
      return (
        <section className="surface">
          <LoadingBlock />
        </section>
      );
    }
    if (detail.error) {
      return (
        <section className="surface">
          <ErrorPanel error={detail.error} onRetry={() => detail.refetch()} />
        </section>
      );
    }
    if (!detail.data) {
      return null;
    }
    const technology = detail.data.technology;
    return (
      <>
        <section className="surface framework-summary">
          <header className="framework-summary-header">
            <div className="framework-title-lockup">
              <span className="framework-large-icon">
                <Boxes size={23} />
              </span>
              <div>
                <span className="eyebrow">
                  {t(`technology.category.${technology.category}`, {
                    defaultValue: technology.category
                  })}
                </span>
                <h2>{technology.name}</h2>
                <code>{technology.technologyKey}</code>
              </div>
            </div>
            <StatusBadge value={technology.status} kind="certainty" />
          </header>
          <div className="evidence-metrics">
            <div>
              <span>{t("frameworks.metrics.api")}</span>
              <strong>{formatNumber(technology.apiEvidenceCount, i18n.language)}</strong>
            </div>
            <div>
              <span>{t("frameworks.metrics.config")}</span>
              <strong>
                {formatNumber(technology.configurationEvidenceCount, i18n.language)}
              </strong>
            </div>
            <div>
              <span>{t("frameworks.metrics.dependency")}</span>
              <strong>
                {formatNumber(technology.dependencyEvidenceCount, i18n.language)}
              </strong>
            </div>
            <div>
              <span>{t("frameworks.metrics.total")}</span>
              <strong>{formatNumber(technology.totalEvidenceCount, i18n.language)}</strong>
            </div>
          </div>
          <div className="framework-interpretation">
            <CircleHelp size={17} />
            <div>
              <strong>{t("frameworks.interpretationTitle")}</strong>
              <p>
                {technology.status === "IDENTIFIED"
                  ? t("frameworks.interpretationDeclared")
                  : t("frameworks.interpretationAnalyzed")}
              </p>
            </div>
          </div>
          <footer className="framework-summary-footer">
            <code>{technology.ruleIds || "—"}</code>
            <span>
              {t("frameworks.analyzedAt", {
                date: formatDate(technology.analyzedAt, i18n.language)
              })}
            </span>
            <span>v{technology.analyzerVersion}</span>
          </footer>
        </section>

        <section className="surface framework-evidence">
          <header className="section-header">
            <div>
              <span className="eyebrow">{t("frameworks.evidenceEyebrow")}</span>
              <h2>
                {t("frameworks.evidenceTitle", {
                  count: detail.data.evidence.length
                })}
              </h2>
            </div>
          </header>
          {detail.data.evidence.length === 0 ? (
            <div className="section-empty">{t("frameworks.noEvidence")}</div>
          ) : (
            <div className="framework-evidence-list">
              {detail.data.evidence.map((finding) => (
                <article key={finding.id} className="framework-evidence-row">
                  <div className="framework-evidence-main">
                    <div className="evidence-file">
                      <FileCode2 size={16} />
                      <div>
                        <code>{finding.filePath}</code>
                        <span>
                          {finding.lineStart
                            ? t("inventory.line", { line: finding.lineStart })
                            : t("frameworks.noLine")}
                        </span>
                      </div>
                    </div>
                    <p>{finding.summary}</p>
                    {finding.evidence ? (
                      <pre>
                        <code>{finding.evidence}</code>
                      </pre>
                    ) : null}
                  </div>
                  <div className="framework-evidence-side">
                    <StatusBadge value={finding.certainty} kind="certainty" />
                    <code>{finding.ruleId}</code>
                    {finding.fileId ? (
                      <Link
                        className="text-link"
                        to={`/projects/${projectId}/inventory?fileId=${finding.fileId}`}
                      >
                        {t("frameworks.inspectFile")}
                        <ArrowRight size={14} />
                      </Link>
                    ) : null}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </>
    );
  }
}
