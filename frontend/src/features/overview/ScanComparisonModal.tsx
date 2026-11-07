import { useQuery } from "@tanstack/react-query";
import { ArrowRight, GitCompare } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import LoadingBlock from "../../components/LoadingBlock";
import Modal from "../../components/Modal";
import { api } from "../../lib/api";
import { formatBytes, formatDate, formatNumber } from "../../lib/format";
import {
  completedScans,
  defaultScanComparisonBase,
  defaultScanComparisonCurrent,
  groupScanComparisonRows,
  scanComparisonBaseOptions,
  shortHash
} from "../../lib/scanComparison";
import type { Scan, ScanComparisonFile } from "../../lib/types";

interface ScanComparisonModalProps {
  projectId: string;
  initialScanId?: string;
  open: boolean;
  onClose: () => void;
}

const EMPTY_SCANS: Scan[] = [];

export default function ScanComparisonModal({
  projectId,
  initialScanId,
  open,
  onClose
}: ScanComparisonModalProps) {
  const { t, i18n } = useTranslation();
  const scansQuery = useQuery({
    queryKey: ["scans", projectId],
    queryFn: () => api.scans(projectId),
    enabled: open && Boolean(projectId)
  });
  const scans = scansQuery.data ?? EMPTY_SCANS;
  const completed = completedScans(scans);
  const [scanId, setScanId] = useState("");
  const [baseScanId, setBaseScanId] = useState("");
  const initialized = useRef(false);

  useEffect(() => {
    if (!open) {
      initialized.current = false;
      return;
    }
    if (initialized.current || scans.length === 0) {
      return;
    }
    const next = defaultScanComparisonCurrent(scans, initialScanId);
    setScanId(next);
    setBaseScanId(defaultScanComparisonBase(scans, next));
    initialized.current = true;
  }, [open, initialScanId, scans]);

  const baseOptions = scanComparisonBaseOptions(scans, scanId);
  const comparisonQuery = useQuery({
    queryKey: ["scan-comparison", projectId, scanId, baseScanId],
    queryFn: () => api.scanComparison(projectId, scanId, baseScanId),
    enabled:
      open &&
      Boolean(projectId) &&
      Boolean(scanId) &&
      Boolean(baseScanId) &&
      scanId !== baseScanId
  });
  const comparison = comparisonQuery.data;
  const groups = comparison ? groupScanComparisonRows(comparison.rows) : [];

  const changeCurrent = (next: string) => {
    setScanId(next);
    setBaseScanId(defaultScanComparisonBase(scans, next));
  };

  return (
    <Modal
      open={open}
      title={t("scanCompare.title")}
      description={
        comparison
          ? t("scanCompare.description", {
              current: comparison.scan.sequence,
              base: comparison.baseScan.sequence
            })
          : t("scanCompare.descriptionPending")
      }
      onClose={onClose}
    >
      {scansQuery.isLoading ? <LoadingBlock /> : null}
      {scansQuery.error ? (
        <p className="inline-error">{scansQuery.error.message}</p>
      ) : null}
      {!scansQuery.isLoading && completed.length < 2 ? (
        <p className="migration-note">{t("scanCompare.needTwoScans")}</p>
      ) : null}
      {completed.length >= 2 ? (
        <div className="scan-comparison-body">
          <div className="scan-comparison-selectors">
            <label className="migration-comparison-base">
              <span>{t("scanCompare.currentLabel")}</span>
              <select
                value={scanId}
                onChange={(event) => changeCurrent(event.target.value)}
              >
                {completed.map((scan) => (
                  <option key={scan.id} value={scan.id}>
                    {t("scanCompare.scanOption", {
                      number: scan.sequence,
                      date: formatDate(
                        scan.finishedAt || scan.createdAt,
                        i18n.language
                      )
                    })}
                  </option>
                ))}
              </select>
            </label>
            <label className="migration-comparison-base">
              <span>{t("scanCompare.baseLabel")}</span>
              <select
                value={baseScanId}
                onChange={(event) => setBaseScanId(event.target.value)}
              >
                {baseOptions.map((scan) => (
                  <option key={scan.id} value={scan.id}>
                    {t("scanCompare.scanOption", {
                      number: scan.sequence,
                      date: formatDate(
                        scan.finishedAt || scan.createdAt,
                        i18n.language
                      )
                    })}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {baseOptions.length === 0 ? (
            <p className="migration-note">{t("scanCompare.noBaseScans")}</p>
          ) : null}
          {comparisonQuery.isLoading ? <LoadingBlock /> : null}
          {comparisonQuery.error ? (
            <p className="inline-error">{comparisonQuery.error.message}</p>
          ) : null}
          {comparison ? (
            <>
              <div className="migration-comparison-metrics scan-comparison-metrics">
                {(
                  ["added", "removed", "changed", "unchanged", "unknown"] as const
                ).map((key) => (
                  <div
                    key={key}
                    className={`migration-comparison-metric scan-comparison-${key}`}
                  >
                    <span>{t(`scanCompare.metrics.${key}`)}</span>
                    <strong>
                      {formatNumber(comparison.summary[key], i18n.language)}
                    </strong>
                  </div>
                ))}
              </div>
              {comparison.truncated ? (
                <p className="scan-comparison-note">
                  {t("scanCompare.truncated", { count: comparison.rowLimit })}
                </p>
              ) : null}
              {groups.length === 0 ? (
                <div className="migration-comparison-empty">
                  <GitCompare size={18} />
                  <span>{t("scanCompare.empty")}</span>
                </div>
              ) : null}
              {groups.map((group) => (
                <section key={group.status} className="migration-comparison-group">
                  <header>
                    <span
                      className={`migration-diff-status migration-diff-${group.status.toLowerCase()}`}
                    >
                      {t(`scanCompare.status.${group.status}`)}
                    </span>
                    <span className="migration-comparison-count">
                      {t("scanCompare.groupCount", { count: group.rows.length })}
                    </span>
                  </header>
                  <div className="migration-comparison-list">
                    {group.rows.map((row) => (
                      <article
                        key={`${group.status}-${row.relativePath}`}
                        className="migration-comparison-row"
                      >
                        <div className="migration-comparison-title">
                          <strong>{row.relativePath}</strong>
                          <div className="scan-comparison-files">
                            <ScanFileFacts
                              label={t("scanCompare.before")}
                              file={row.before}
                            />
                            <ArrowRight size={12} />
                            <ScanFileFacts
                              label={t("scanCompare.after")}
                              file={row.after}
                            />
                          </div>
                          {row.status === "UNKNOWN" ? (
                            <p className="scan-comparison-note">
                              {t("scanCompare.unknownNote")}
                            </p>
                          ) : null}
                        </div>
                      </article>
                    ))}
                  </div>
                </section>
              ))}
            </>
          ) : null}
        </div>
      ) : null}
    </Modal>
  );
}

function ScanFileFacts({
  label,
  file
}: {
  label: string;
  file?: ScanComparisonFile;
}) {
  const { t, i18n } = useTranslation();
  return (
    <div className="scan-comparison-file">
      <span className="scan-comparison-file-label">{label}</span>
      {file ? (
        <div className="migration-row-facts">
          <span className="migration-chip">{file.fileType}</span>
          <span className="migration-chip">
            {formatBytes(file.sizeBytes, i18n.language)}
          </span>
          <span className="migration-chip">
            {file.sha256
              ? shortHash(file.sha256)
              : t("scanCompare.noHash")}
          </span>
        </div>
      ) : (
        <span className="scan-comparison-absent">{t("scanCompare.none")}</span>
      )}
    </div>
  );
}
