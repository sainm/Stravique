import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  ExternalLink,
  FileCode2,
  Link2,
  RotateCcw,
  ShieldAlert,
  Wand2
} from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import Modal from "../../components/Modal";
import StatusBadge from "../../components/StatusBadge";
import { api } from "../../lib/api";
import { formatDate } from "../../lib/format";

interface MigrationInspectorProps {
  projectId: string;
  planId: string;
  itemId: string;
  onClose: () => void;
  onSelectItem: (itemId: string) => void;
  onToggleScope: (itemId: string, included: boolean) => void;
  scopePending: boolean;
  scopeError?: string;
}

export default function MigrationInspector({
  projectId,
  planId,
  itemId,
  onClose,
  onSelectItem,
  onToggleScope,
  scopePending,
  scopeError
}: MigrationInspectorProps) {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [confirmed, setConfirmed] = useState(false);
  const detail = useQuery({
    queryKey: ["migration-item", projectId, planId, itemId],
    queryFn: () => api.migrationItem(projectId, planId, itemId)
  });
  const supported =
    detail.data?.item.itemType === "COMPONENT_BEAN" &&
    detail.data?.item.targetKey === "SPRING_DI";
  const proposals = useQuery({
    queryKey: ["transformation-proposals", projectId, planId, itemId],
    queryFn: () => api.transformationProposals(projectId, planId, itemId),
    enabled: supported
  });
  const runs = useQuery({
    queryKey: ["transformation-runs", projectId, planId, itemId],
    queryFn: () => api.transformationRuns(projectId, planId, itemId),
    enabled: supported
  });
  const proposeMutation = useMutation({
    mutationFn: () => api.proposeTransformation(projectId, planId, itemId),
    onSuccess: async () => {
      setConfirmed(false);
      await refreshTransformation();
    }
  });
  const applyMutation = useMutation({
    mutationFn: (proposalId: string) =>
      api.applyTransformation(projectId, proposalId, true),
    onSuccess: async () => {
      setConfirmed(false);
      await refreshTransformation();
    }
  });
  const restoreMutation = useMutation({
    mutationFn: (runId: string) => api.restoreTransformation(projectId, runId, true),
    onSuccess: async () => {
      setConfirmed(false);
      await refreshTransformation();
    }
  });

  const refreshTransformation = async () => {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: ["transformation-proposals", projectId, planId, itemId]
      }),
      queryClient.invalidateQueries({
        queryKey: ["transformation-runs", projectId, planId, itemId]
      })
    ]);
  };

  useEffect(() => {
    setConfirmed(false);
    proposeMutation.reset();
    applyMutation.reset();
    restoreMutation.reset();
  }, [itemId]);

  const latestProposal = proposals.data?.[0];
  const appliedRun = runs.data?.find((run) => run.status === "APPLIED");
  const transformationError =
    proposeMutation.error || applyMutation.error || restoreMutation.error;

  return (
    <Modal
      open
      title={
        detail.data?.item.title ||
        t("migration.detailTitle")
      }
      description={detail.data?.item.primaryFilePath}
      onClose={onClose}
    >
      {detail.isLoading ? <LoadingBlock /> : null}
      {detail.error ? (
        <ErrorPanel error={detail.error} onRetry={() => detail.refetch()} />
      ) : null}
      {detail.data ? (
        <div className="migration-inspector">
          <div className="migration-inspector-badges">
            <StatusBadge value={detail.data.item.status} kind="migration" />
            <span className="migration-chip">
              {t(`migration.itemTypes.${detail.data.item.itemType}`, {
                defaultValue: detail.data.item.itemType
              })}
            </span>
            <span className="migration-chip">
              {t(`migration.targets.${detail.data.item.targetKey}`, {
                defaultValue: detail.data.item.targetKey
              })}
            </span>
            <span className="migration-chip">
              {t(`migration.risks.${detail.data.item.riskLevel}`, {
                defaultValue: detail.data.item.riskLevel
              })}
            </span>
          </div>

          <dl className="migration-facts">
            <div>
              <dt>{t("migration.detail.phase")}</dt>
              <dd>{t("migration.phaseLabel", { value: detail.data.item.phase })}</dd>
            </div>
            <div>
              <dt>{t("migration.detail.priority")}</dt>
              <dd>{t("migration.priorityLabel", { value: detail.data.item.priority })}</dd>
            </div>
            <div>
              <dt>{t("migration.detail.automation")}</dt>
              <dd>
                {t(`migration.automations.${detail.data.item.automationLevel}`, {
                  defaultValue: detail.data.item.automationLevel
                })}
              </dd>
            </div>
            <div>
              <dt>{t("migration.detail.reason")}</dt>
              <dd>
                {t(`migration.reasons.${detail.data.item.reasonCode}`, {
                  defaultValue: detail.data.item.reasonCode
                })}
              </dd>
            </div>
            <div>
              <dt>{t("migration.detail.evidence")}</dt>
              <dd>
                {t("migration.evidenceCount", {
                  count: detail.data.item.findingCount
                })}
              </dd>
            </div>
            <div>
              <dt>{t("migration.detail.scope")}</dt>
              <dd>
                {detail.data.item.included
                  ? t("migration.included")
                  : t("migration.excluded")}
              </dd>
            </div>
          </dl>

          {detail.data.item.primaryFileId ? (
            <Link
              className="text-link migration-file-link"
              to={`/projects/${projectId}/inventory?fileId=${detail.data.item.primaryFileId}`}
            >
              <ExternalLink size={14} />
              {t("migration.openInInventory")}
            </Link>
          ) : null}

          <div className="migration-scope-action">
            <div>
              <strong>{t("migration.scopeTitle")}</strong>
              <p>{t("migration.scopeDescription")}</p>
            </div>
            <button
              className={`button button-small ${
                detail.data.item.included ? "button-ghost" : "button-secondary"
              }`}
              disabled={scopePending}
              onClick={() =>
                onToggleScope(detail.data!.item.id, !detail.data!.item.included)
              }
            >
              {detail.data.item.included
                ? t("migration.excludeFromPlan")
                : t("migration.includeInPlan")}
            </button>
          </div>
          {scopeError ? <p className="inline-error">{scopeError}</p> : null}

          <section className="migration-inspector-section migration-transformation">
            <header>
              <span className="eyebrow">{t("migration.transformation.title")}</span>
              {supported && latestProposal ? (
                <span className="migration-count">
                  {latestProposal.ruleId} · v{latestProposal.ruleVersion}
                </span>
              ) : null}
            </header>
            {!supported ? (
              <p className="migration-muted">
                {t("migration.transformation.unsupported")}
              </p>
            ) : (
              <>
                <p className="migration-note">
                  {t("migration.transformation.description")}
                </p>
                <p className="migration-transformation-warning">
                  <ShieldAlert size={14} />
                  <span>{t("migration.transformation.sourceWarning")}</span>
                </p>
                <div className="migration-transformation-actions">
                  <button
                    className="button button-secondary button-small"
                    disabled={proposeMutation.isPending}
                    onClick={() => proposeMutation.mutate()}
                  >
                    <Wand2 size={14} />
                    {proposeMutation.isPending
                      ? t("migration.transformation.generating")
                      : latestProposal
                        ? t("migration.transformation.regeneratePreview")
                        : t("migration.transformation.generatePreview")}
                  </button>
                  <span className="migration-chip">
                    <FileCode2 size={12} />
                    {t("migration.transformation.scope")}
                  </span>
                </div>
                {proposals.isLoading ? <LoadingBlock /> : null}
                {proposals.error ? (
                  <ErrorPanel
                    error={proposals.error}
                    onRetry={() => proposals.refetch()}
                  />
                ) : null}
                {transformationError ? (
                  <p className="inline-error">{transformationError.message}</p>
                ) : null}
                {latestProposal ? (
                  <div className="migration-proposal">
                    <div className="migration-proposal-head">
                      <StatusBadge
                        value={latestProposal.status}
                        kind="transformation"
                      />
                      <span className="migration-chip">
                        {t(
                          `migration.transformation.validation.${latestProposal.validationStatus}`,
                          { defaultValue: latestProposal.validationStatus }
                        )}
                      </span>
                    </div>
                    <pre className="migration-diff">{latestProposal.diff}</pre>
                    {latestProposal.status === "PROPOSED" ||
                    latestProposal.status === "APPLIED" ? (
                      <label className="migration-confirm">
                        <input
                          type="checkbox"
                          checked={confirmed}
                          onChange={(event) => setConfirmed(event.target.checked)}
                        />
                        <span>{t("migration.transformation.confirm")}</span>
                      </label>
                    ) : null}
                    {latestProposal.status === "PROPOSED" ? (
                      <div className="migration-transformation-actions">
                        <button
                          className="button button-primary button-small"
                          disabled={!confirmed || applyMutation.isPending}
                          onClick={() => applyMutation.mutate(latestProposal.id)}
                        >
                          <Wand2 size={14} />
                          {applyMutation.isPending
                            ? t("migration.transformation.applying")
                            : t("migration.transformation.apply")}
                        </button>
                      </div>
                    ) : null}
                    {latestProposal.status === "APPLIED" ? (
                      <p className="migration-note">
                        {t("migration.transformation.appliedNote")}
                      </p>
                    ) : null}
                    {latestProposal.status === "RESTORED" ? (
                      <p className="migration-note">
                        {t("migration.transformation.restoredNote")}
                      </p>
                    ) : null}
                    {latestProposal.status === "SUPERSEDED" ? (
                      <p className="migration-note">
                        {t("migration.transformation.supersededNote")}
                      </p>
                    ) : null}
                  </div>
                ) : !proposals.isLoading ? (
                  <p className="migration-muted">
                    {t("migration.transformation.noPreview")}
                  </p>
                ) : null}
                {appliedRun ? (
                  <div className="migration-run">
                    <div>
                      <strong>{t("migration.transformation.runTitle")}</strong>
                      <small>
                        {formatDate(appliedRun.createdAt, i18n.language)} ·{" "}
                        {t("migration.transformation.backupNote")}
                      </small>
                    </div>
                    <button
                      className="button button-ghost button-small"
                      disabled={!confirmed || restoreMutation.isPending}
                      onClick={() => restoreMutation.mutate(appliedRun.id)}
                    >
                      <RotateCcw size={14} />
                      {restoreMutation.isPending
                        ? t("migration.transformation.restoring")
                        : t("migration.transformation.restore")}
                    </button>
                  </div>
                ) : null}
                {runs.data && runs.data.length > 0 ? (
                  <div className="migration-run-history">
                    <span className="eyebrow">
                      {t("migration.transformation.historyTitle")}
                    </span>
                    {runs.data.map((run) => (
                      <div className="migration-run-row" key={run.id}>
                        <StatusBadge value={run.status} kind="transformation" />
                        <code>{run.relativePath}</code>
                        <span>{formatDate(run.createdAt, i18n.language)}</span>
                      </div>
                    ))}
                  </div>
                ) : null}
              </>
            )}
          </section>

          <section className="migration-inspector-section">
            <header>
              <span className="eyebrow">{t("migration.evidenceTitle")}</span>
              <span className="migration-count">
                {t("migration.evidenceCount", {
                  count: detail.data.findings.length
                })}
              </span>
            </header>
            {detail.data.findings.length === 0 ? (
              <p className="migration-muted">{t("migration.noFindings")}</p>
            ) : (
              <div className="migration-evidence-list">
                {detail.data.findings.map((finding) => (
                  <article className="migration-evidence-item" key={finding.id}>
                    <div className="migration-evidence-meta">
                      <code>{finding.ruleId}</code>
                      {finding.lineStart ? (
                        <span>
                          {t("migration.line", { line: finding.lineStart })}
                        </span>
                      ) : null}
                      <StatusBadge value={finding.certainty} kind="certainty" />
                    </div>
                    <p>{finding.summary}</p>
                    <pre>{finding.evidence}</pre>
                    {finding.fileId ? (
                      <Link
                        className="text-link"
                        to={`/projects/${projectId}/inventory?fileId=${finding.fileId}`}
                      >
                        <ExternalLink size={13} />
                        {finding.filePath}
                      </Link>
                    ) : (
                      <code>{finding.filePath}</code>
                    )}
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="migration-inspector-section">
            <header>
              <span className="eyebrow">{t("migration.dependsOn")}</span>
              <span className="migration-count">
                {t("migration.dependencyCount", {
                  count: detail.data.dependencies.length
                })}
              </span>
            </header>
            {detail.data.dependencies.length === 0 ? (
              <p className="migration-muted">{t("migration.noDependencies")}</p>
            ) : (
              <div className="migration-dependency-list">
                {detail.data.dependencies.map((dependency) => (
                  <button
                    className="migration-dependency-row"
                    key={dependency.id}
                    onClick={() => onSelectItem(dependency.toItemId)}
                  >
                    <Link2 size={14} />
                    <span>{dependency.toTitle}</span>
                    <small>
                      {dependency.relationType}
                      {dependency.lineStart
                        ? ` · ${dependency.evidencePath}:${dependency.lineStart}`
                        : ` · ${dependency.evidencePath}`}
                    </small>
                    <ArrowRight size={14} />
                  </button>
                ))}
              </div>
            )}
          </section>

          <section className="migration-inspector-section">
            <header>
              <span className="eyebrow">{t("migration.requiredBy")}</span>
              <span className="migration-count">
                {t("migration.dependentCount", {
                  count: detail.data.dependents.length
                })}
              </span>
            </header>
            {detail.data.dependents.length === 0 ? (
              <p className="migration-muted">{t("migration.noDependents")}</p>
            ) : (
              <div className="migration-dependency-list">
                {detail.data.dependents.map((dependency) => (
                  <button
                    className="migration-dependency-row"
                    key={dependency.id}
                    onClick={() => onSelectItem(dependency.fromItemId)}
                  >
                    <ShieldAlert size={14} />
                    <span>{dependency.fromTitle}</span>
                    <small>
                      {dependency.relationType}
                      {dependency.lineStart
                        ? ` · ${dependency.evidencePath}:${dependency.lineStart}`
                        : ` · ${dependency.evidencePath}`}
                    </small>
                    <ArrowRight size={14} />
                  </button>
                ))}
              </div>
            )}
          </section>
        </div>
      ) : null}
    </Modal>
  );
}
