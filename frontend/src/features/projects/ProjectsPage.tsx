import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  FolderCode,
  FolderPlus,
  HardDrive,
  Layers3,
  Plus,
  ScanSearch,
  Settings2,
  ShieldCheck
} from "lucide-react";
import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";
import EmptyState from "../../components/EmptyState";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import Modal from "../../components/Modal";
import PageHeader from "../../components/PageHeader";
import StatusBadge from "../../components/StatusBadge";
import { api } from "../../lib/api";
import { formatDate } from "../../lib/format";
import type { ProjectInput } from "../../lib/types";

const initialForm: ProjectInput = {
  name: "",
  description: "",
  sourcePath: "",
  targetFramework: "",
  analysisScope: "FULL"
};

export default function ProjectsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState<ProjectInput>(initialForm);
  const projects = useQuery({
    queryKey: ["projects"],
    queryFn: api.projects
  });
  const runtime = useQuery({
    queryKey: ["runtime"],
    queryFn: api.runtime,
    staleTime: Infinity
  });
  const createProject = useMutation({
    mutationFn: () => api.createProject(form),
    onSuccess: async (project) => {
      await queryClient.invalidateQueries({ queryKey: ["projects"] });
      setCreateOpen(false);
      setForm(initialForm);
      navigate(`/projects/${project.id}/overview`);
    }
  });

  const openCreate = () => {
    createProject.reset();
    setCreateOpen(true);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    createProject.mutate();
  };

  return (
    <>
      <PageHeader
        eyebrow={t("projects.eyebrow")}
        title={t("projects.title")}
        description={t("projects.description")}
        actions={
          <button className="button button-primary" onClick={openCreate}>
            <FolderPlus size={16} />
            {t("projects.create")}
          </button>
        }
      />

      <section className="registry-summary" aria-label={t("projects.registrySummary")}>
        <div className="registry-summary-main">
          <div className="summary-icon">
            <Layers3 size={20} />
          </div>
          <div>
            <strong>
              {projects.data
                ? t("projects.registeredCount", { count: projects.data.length })
                : t("projects.registryLoading")}
            </strong>
            <p>{t("projects.registryHint")}</p>
          </div>
        </div>
        <div className="registry-policy">
          <ShieldCheck size={16} />
          <span>{t("projects.readOnlyPolicy")}</span>
          <span className="policy-divider" />
          <HardDrive size={15} />
          <code>{t("projects.limit", {
            size: runtime.data
              ? Math.round(runtime.data.maxFileBytes / 1024 / 1024)
              : "—"
          })}</code>
        </div>
      </section>

      {projects.isLoading ? <LoadingBlock /> : null}
      {projects.error ? (
        <ErrorPanel error={projects.error} onRetry={() => projects.refetch()} />
      ) : null}
      {projects.data?.length === 0 ? (
        <section className="surface empty-projects">
          <EmptyState
            icon={ScanSearch}
            title={t("projects.emptyTitle")}
            description={t("projects.emptyDescription")}
            action={
              <button className="button button-primary" onClick={openCreate}>
                <Plus size={16} />
                {t("projects.create")}
              </button>
            }
          />
        </section>
      ) : null}
      {projects.data && projects.data.length > 0 ? (
        <section className="project-registry surface" aria-label={t("projects.listLabel")}>
          <div className="project-table-head" aria-hidden="true">
            <span>{t("projects.columns.project")}</span>
            <span>{t("projects.columns.stack")}</span>
            <span>{t("projects.columns.lastScan")}</span>
            <span>{t("projects.columns.status")}</span>
            <span />
          </div>
          {projects.data.map((project) => (
            <article className="project-row" key={project.id}>
              <div className="project-identity">
                <div className="project-glyph">
                  <FolderCode size={19} strokeWidth={1.7} />
                </div>
                <div>
                  <Link to={`/projects/${project.id}/overview`}>{project.name}</Link>
                  <code>{project.sourcePath}</code>
                </div>
              </div>
              <div className="stack-cell">
                <span className="technical-value">
                  {project.buildTool === "UNKNOWN" ? t("common.notDetected") : project.buildTool}
                </span>
                <small>
                  {project.sourceFramework ||
                    (project.projectType === "UNKNOWN"
                      ? t("projects.awaitingScan")
                      : t(`projectType.${project.projectType}`, {
                          defaultValue: project.projectType
                        }))}
                </small>
              </div>
              <div className="date-cell">
                {project.lastScanAt
                  ? formatDate(project.lastScanAt, i18n.language)
                  : t("common.never")}
              </div>
              <div>
                {project.latestScan ? (
                  <StatusBadge value={project.latestScan.status} kind="scan" />
                ) : (
                  <span className="subtle-pill">{t("projects.notScanned")}</span>
                )}
              </div>
              <div className="project-row-actions">
                <Link
                  className="icon-button"
                  to={`/projects/${project.id}/settings`}
                  aria-label={t("projects.openSettings")}
                  title={t("projects.openSettings")}
                >
                  <Settings2 size={16} />
                </Link>
                <Link
                  className="button button-quiet button-small"
                  to={`/projects/${project.id}/overview`}
                >
                  {t("common.open")}
                  <ArrowRight size={14} />
                </Link>
              </div>
            </article>
          ))}
        </section>
      ) : null}

      <section className="access-policy">
        <div>
          <span className="eyebrow">{t("projects.accessEyebrow")}</span>
          <h2>{t("projects.accessTitle")}</h2>
          <p>{t("projects.accessDescription")}</p>
        </div>
        <div className="root-list">
          {runtime.data?.allowedScanRoots.slice(0, 3).map((root) => (
            <code key={root}>{root}</code>
          ))}
          {runtime.data && runtime.data.allowedScanRoots.length > 3 ? (
            <span>+{runtime.data.allowedScanRoots.length - 3}</span>
          ) : null}
        </div>
      </section>

      <Modal
        open={createOpen}
        title={t("projects.createTitle")}
        description={t("projects.createDescription")}
        onClose={() => {
          setCreateOpen(false);
          createProject.reset();
        }}
      >
        <form className="modal-form" onSubmit={submit}>
          <div className="form-grid">
            <label className="field field-wide">
              <span>{t("form.name")}</span>
              <input
                required
                maxLength={160}
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                placeholder={t("form.namePlaceholder")}
                autoFocus
              />
            </label>
            <label className="field field-wide">
              <span>{t("form.sourcePath")}</span>
              <input
                required
                value={form.sourcePath}
                onChange={(event) =>
                  setForm({ ...form, sourcePath: event.target.value })
                }
                placeholder={t("form.sourcePathPlaceholder")}
                spellCheck={false}
              />
              <small>{t("form.sourcePathHint")}</small>
            </label>
            <label className="field field-wide">
              <span>{t("form.description")}</span>
              <textarea
                rows={3}
                maxLength={2000}
                value={form.description}
                onChange={(event) =>
                  setForm({ ...form, description: event.target.value })
                }
                placeholder={t("form.descriptionPlaceholder")}
              />
            </label>
            <label className="field field-wide">
              <span>{t("form.targetFramework")}</span>
              <input
                maxLength={160}
                value={form.targetFramework}
                onChange={(event) =>
                  setForm({ ...form, targetFramework: event.target.value })
                }
                placeholder={t("form.targetFrameworkPlaceholder")}
              />
            </label>
          </div>
          <div className="allowed-roots-inline">
            <ShieldCheck size={15} />
            <div>
              <strong>{t("form.allowedRoots")}</strong>
              {runtime.data?.allowedScanRoots.map((root) => (
                <code key={root}>{root}</code>
              ))}
            </div>
          </div>
          {createProject.error ? <ErrorPanel error={createProject.error} /> : null}
          <footer className="modal-actions">
            <button
              type="button"
              className="button button-ghost"
              onClick={() => setCreateOpen(false)}
            >
              {t("common.cancel")}
            </button>
            <button
              type="submit"
              className="button button-primary"
              disabled={createProject.isPending}
            >
              <FolderPlus size={16} />
              {createProject.isPending
                ? t("common.creating")
                : t("projects.createAndOpen")}
            </button>
          </footer>
        </form>
      </Modal>
    </>
  );
}
