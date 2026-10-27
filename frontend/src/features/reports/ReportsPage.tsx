import { useQuery } from "@tanstack/react-query";
import {
  Braces,
  Download,
  FileJson2,
  FileSpreadsheet,
  FileText,
  ScrollText,
  ShieldCheck
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import EmptyState from "../../components/EmptyState";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import PageHeader from "../../components/PageHeader";
import ScanButton from "../../components/ScanButton";
import { api } from "../../lib/api";
import { formatDate, formatNumber } from "../../lib/format";
import type { ReportDescriptor } from "../../lib/types";

const reportIcons = {
  FILES: FileText,
  STRUCTURE: Braces,
  TECHNOLOGY: ScrollText,
  FRAMEWORKS: ScrollText,
  CONFIGURATION: FileText,
  PARSE_ERRORS: FileJson2,
  REVIEWS: ShieldCheck
};

const formatIcons = {
  csv: FileSpreadsheet,
  json: FileJson2,
  markdown: FileText
};

const formatContentTypes: Record<string, string> = {
  csv: "CSV",
  json: "JSON",
  markdown: "Markdown"
};

export default function ReportsPage() {
  const { projectId = "" } = useParams();
  const { t, i18n } = useTranslation();
  const project = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => api.project(projectId)
  });
  const reports = useQuery({
    queryKey: ["reports", projectId],
    queryFn: () => api.reports(projectId)
  });

  return (
    <>
      <PageHeader
        eyebrow={t("reports.eyebrow")}
        title={t("reports.title")}
        description={t("reports.description")}
        actions={
          <ScanButton
            projectId={projectId}
            active={["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(
              project.data?.latestScan?.status || ""
            )}
            compact
          />
        }
      />

      <section className="report-policy">
        <ShieldCheck size={18} />
        <div>
          <strong>{t("reports.policyTitle")}</strong>
          <p>{t("reports.policyDescription")}</p>
        </div>
        {project.data?.latestScan ? (
          <span>
            {t("reports.scanContext", {
              sequence: project.data.latestScan.sequence,
              date: formatDate(
                project.data.latestScan.finishedAt ||
                  project.data.latestScan.createdAt,
                i18n.language
              )
            })}
          </span>
        ) : null}
      </section>

      {!project.data?.latestScan && !project.isLoading ? (
        <section className="surface">
          <EmptyState
            icon={ScrollText}
            title={t("reports.noScanTitle")}
            description={t("reports.noScanDescription")}
            action={<ScanButton projectId={projectId} />}
          />
        </section>
      ) : null}
      {reports.isLoading ? <LoadingBlock /> : null}
      {reports.error ? (
        <ErrorPanel error={reports.error} onRetry={() => reports.refetch()} />
      ) : null}

      {reports.data ? (
        <section className="report-catalog surface">
          <header className="section-header report-catalog-header">
            <div>
              <span className="eyebrow">{t("reports.catalogEyebrow")}</span>
              <h2>{t("reports.catalogTitle")}</h2>
            </div>
            <p>{t("reports.catalogDescription")}</p>
          </header>
          <div className="report-list">
            {reports.data.map((report) => (
              <ReportRow
                key={report.type}
                report={report}
                projectId={projectId}
                scanId={report.scanId}
              />
            ))}
          </div>
        </section>
      ) : null}
    </>
  );
}

function ReportRow({
  report,
  projectId,
  scanId
}: {
  report: ReportDescriptor;
  projectId: string;
  scanId?: string;
}) {
  const { t, i18n } = useTranslation();
  const Icon =
    reportIcons[report.type as keyof typeof reportIcons] || ScrollText;
  const available = Boolean(scanId);
  return (
    <article className="report-row">
      <div className="report-icon">
        <Icon size={19} strokeWidth={1.7} />
      </div>
      <div className="report-copy">
        <h3>{t(`reports.types.${report.type}.title`)}</h3>
        <p>{t(`reports.types.${report.type}.description`)}</p>
      </div>
      <div className="report-count">
        <strong>{formatNumber(report.rowCount, i18n.language)}</strong>
        <span>{t("reports.rows")}</span>
      </div>
      <div className="report-formats">
        {report.formats.map((format) => {
          const FormatIcon =
            formatIcons[format as keyof typeof formatIcons] || Download;
          return available ? (
            <a
              key={format}
              className="format-button"
              href={api.reportUrl(projectId, report.type, format, scanId, i18n.language)}
              download
              aria-label={t("reports.downloadFormat", {
                format: formatContentTypes[format] || format,
                report: t(`reports.types.${report.type}.title`)
              })}
              title={t("reports.downloadFormat", {
                format: formatContentTypes[format] || format,
                report: t(`reports.types.${report.type}.title`)
              })}
            >
              <FormatIcon size={14} />
              {formatContentTypes[format] || format}
            </a>
          ) : (
            <span key={format} className="format-button format-disabled">
              <FormatIcon size={14} />
              {formatContentTypes[format] || format}
            </span>
          );
        })}
      </div>
      <div className="report-status">
        {available ? (
          <span className="report-ready">{t("reports.ready")}</span>
        ) : (
          <span>{t("reports.unavailable")}</span>
        )}
      </div>
    </article>
  );
}
