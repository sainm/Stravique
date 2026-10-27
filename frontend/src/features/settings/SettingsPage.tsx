import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CheckCircle2,
  FolderCog,
  Plus,
  Save,
  ShieldCheck,
  Trash2
} from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import PageHeader from "../../components/PageHeader";
import { api } from "../../lib/api";
import type {
  PathOverride,
  PathOverrideInput,
  ProjectInput
} from "../../lib/types";

const projectTypes = [
  "UNKNOWN",
  "MAVEN_SINGLE_MODULE",
  "MAVEN_MULTI_MODULE",
  "GRADLE_SINGLE_MODULE",
  "GRADLE_MULTI_MODULE",
  "ECLIPSE_WEB"
];

const buildTools = ["UNKNOWN", "MAVEN", "GRADLE", "MIXED"];
const analysisScopes = ["FULL", "SOURCE_ONLY", "CONFIGURATION_ONLY"];
const pathRoles = [
  "JAVA_SOURCE",
  "TEST_SOURCE",
  "RESOURCE",
  "WEB_ROOT",
  "JSP_ROOT",
  "STATIC_RESOURCE",
  "CONFIGURATION",
  "SQL"
];

const emptyInput: ProjectInput = {
  name: "",
  description: "",
  sourcePath: "",
  projectType: "",
  jdkVersion: "",
  buildTool: "",
  sourceFramework: "",
  targetFramework: "",
  analysisScope: "FULL",
  excludedDirectories: [],
  excludedFiles: []
};

export default function SettingsPage() {
  const { projectId = "" } = useParams();
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const project = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => api.project(projectId)
  });
  const runtime = useQuery({
    queryKey: ["runtime"],
    queryFn: api.runtime,
    staleTime: Infinity
  });
  const overrides = useQuery({
    queryKey: ["path-overrides", projectId],
    queryFn: () => api.pathOverrides(projectId)
  });
  const [form, setForm] = useState<ProjectInput>(emptyInput);
  const [excludedDirectories, setExcludedDirectories] = useState("");
  const [excludedFiles, setExcludedFiles] = useState("");
  const [pathOverrides, setPathOverrides] = useState<PathOverrideInput[]>([]);
  const [newPath, setNewPath] = useState("");
  const [newRole, setNewRole] = useState("WEB_ROOT");
  const [overrideSaved, setOverrideSaved] = useState(false);

  useEffect(() => {
    if (!project.data) {
      return;
    }
    setForm({
      name: project.data.name,
      description: project.data.description,
      sourcePath: project.data.sourcePath,
      projectType: project.data.projectType,
      jdkVersion: project.data.jdkVersion || "",
      buildTool: project.data.buildTool,
      sourceFramework: project.data.sourceFramework || "",
      targetFramework: project.data.targetFramework || "",
      analysisScope: project.data.analysisScope
    });
    setExcludedDirectories(project.data.excludedDirectories.join("\n"));
    setExcludedFiles(project.data.excludedFiles.join("\n"));
  }, [project.data]);

  useEffect(() => {
    if (overrides.data) {
      setPathOverrides(
        overrides.data.map((item: PathOverride) => ({
          relativePath: item.relativePath,
          role: item.role
        }))
      );
    }
  }, [overrides.data]);

  const saveProject = useMutation({
    mutationFn: () =>
      api.updateProject(projectId, {
        ...form,
        excludedDirectories: toLines(excludedDirectories),
        excludedFiles: toLines(excludedFiles)
      }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(["project", projectId], updated);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["projects"] }),
        queryClient.invalidateQueries({ queryKey: ["overview", projectId] })
      ]);
    }
  });
  const saveOverrides = useMutation({
    mutationFn: () => api.replacePathOverrides(projectId, pathOverrides),
    onSuccess: async (saved) => {
      queryClient.setQueryData(["path-overrides", projectId], saved);
      setOverrideSaved(true);
      window.setTimeout(() => setOverrideSaved(false), 2200);
      await queryClient.invalidateQueries({ queryKey: ["tree", projectId] });
    }
  });

  const addOverride = () => {
    const normalized = newPath.trim().replaceAll("\\", "/").replace(/\/+$/, "");
    if (!normalized || pathOverrides.some((item) => item.relativePath === normalized)) {
      return;
    }
    setPathOverrides([...pathOverrides, { relativePath: normalized, role: newRole }]);
    setNewPath("");
  };

  const submitProject = (event: FormEvent) => {
    event.preventDefault();
    saveProject.mutate();
  };

  if (project.isLoading || overrides.isLoading) {
    return <LoadingBlock />;
  }
  if (project.error) {
    return <ErrorPanel error={project.error} onRetry={() => project.refetch()} />;
  }
  if (!project.data) {
    return null;
  }

  return (
    <>
      <PageHeader
        eyebrow={t("settings.eyebrow")}
        title={t("settings.title")}
        description={t("settings.description")}
        actions={
          <button
            className="button button-primary"
            form="project-settings-form"
            type="submit"
            disabled={saveProject.isPending}
          >
            <Save size={15} />
            {saveProject.isPending ? t("common.saving") : t("common.saveChanges")}
          </button>
        }
      />

      <div className="settings-layout">
        <form
          id="project-settings-form"
          className="surface settings-form"
          onSubmit={submitProject}
        >
          <section className="settings-section">
            <header>
              <span className="eyebrow">{t("settings.identityEyebrow")}</span>
              <h2>{t("settings.identityTitle")}</h2>
              <p>{t("settings.identityDescription")}</p>
            </header>
            <div className="settings-grid">
              <label className="field">
                <span>{t("form.name")}</span>
                <input
                  required
                  maxLength={160}
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                />
              </label>
              <label className="field">
                <span>{t("form.sourcePath")}</span>
                <input
                  required
                  value={form.sourcePath}
                  onChange={(event) =>
                    setForm({ ...form, sourcePath: event.target.value })
                  }
                  spellCheck={false}
                />
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
                />
              </label>
            </div>
          </section>

          <section className="settings-section">
            <header>
              <span className="eyebrow">{t("settings.stackEyebrow")}</span>
              <h2>{t("settings.stackTitle")}</h2>
              <p>{t("settings.stackDescription")}</p>
            </header>
            <div className="settings-grid settings-grid-three">
              <label className="field">
                <span>{t("settings.projectType")}</span>
                <select
                  value={form.projectType}
                  onChange={(event) =>
                    setForm({ ...form, projectType: event.target.value })
                  }
                >
                  {projectTypes.map((type) => (
                    <option key={type} value={type}>
                      {t(`projectType.${type}`, { defaultValue: type })}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>{t("settings.buildTool")}</span>
                <select
                  value={form.buildTool}
                  onChange={(event) =>
                    setForm({ ...form, buildTool: event.target.value })
                  }
                >
                  {buildTools.map((tool) => (
                    <option key={tool} value={tool}>
                      {t(`buildTool.${tool}`, { defaultValue: tool })}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>{t("settings.jdkVersion")}</span>
                <input
                  value={form.jdkVersion}
                  onChange={(event) =>
                    setForm({ ...form, jdkVersion: event.target.value })
                  }
                  placeholder={t("settings.jdkPlaceholder")}
                />
              </label>
              <label className="field">
                <span>{t("form.sourceFramework")}</span>
                <input
                  value={form.sourceFramework}
                  onChange={(event) =>
                    setForm({ ...form, sourceFramework: event.target.value })
                  }
                  placeholder={t("settings.sourceFrameworkPlaceholder")}
                />
              </label>
              <label className="field">
                <span>{t("form.targetFramework")}</span>
                <input
                  value={form.targetFramework}
                  onChange={(event) =>
                    setForm({ ...form, targetFramework: event.target.value })
                  }
                  placeholder={t("form.targetFrameworkPlaceholder")}
                />
              </label>
              <label className="field">
                <span>{t("settings.analysisScope")}</span>
                <select
                  value={form.analysisScope}
                  onChange={(event) =>
                    setForm({ ...form, analysisScope: event.target.value })
                  }
                >
                  {analysisScopes.map((scope) => (
                    <option key={scope} value={scope}>
                      {t(`analysisScope.${scope}`)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </section>

          <section className="settings-section">
            <header>
              <span className="eyebrow">{t("settings.scopeEyebrow")}</span>
              <h2>{t("settings.scopeTitle")}</h2>
              <p>{t("settings.scopeDescription")}</p>
            </header>
            <div className="settings-grid">
              <label className="field">
                <span>{t("settings.excludedDirectories")}</span>
                <textarea
                  rows={5}
                  value={excludedDirectories}
                  onChange={(event) => setExcludedDirectories(event.target.value)}
                  placeholder={t("settings.exclusionsPlaceholder")}
                  spellCheck={false}
                />
              </label>
              <label className="field">
                <span>{t("settings.excludedFiles")}</span>
                <textarea
                  rows={5}
                  value={excludedFiles}
                  onChange={(event) => setExcludedFiles(event.target.value)}
                  placeholder={t("settings.exclusionsPlaceholder")}
                  spellCheck={false}
                />
              </label>
            </div>
            <div className="inline-safety">
              <ShieldCheck size={15} />
              <span>{t("settings.defaultExclusions")}</span>
            </div>
          </section>

          {saveProject.error ? <ErrorPanel error={saveProject.error} /> : null}
          {saveProject.isSuccess ? (
            <div className="save-confirmation" role="status">
              <CheckCircle2 size={15} />
              {t("settings.projectSaved")}
            </div>
          ) : null}
        </form>

        <div className="settings-side">
          <section className="surface path-policy-card">
            <header>
              <FolderCog size={18} />
              <div>
                <h2>{t("settings.pathPolicyTitle")}</h2>
                <p>{t("settings.pathPolicyDescription")}</p>
              </div>
            </header>
            <div className="allowed-root-stack">
              {runtime.data?.allowedScanRoots.map((root) => (
                <code key={root}>{root}</code>
              ))}
            </div>
            <div className="policy-facts">
              <div>
                <strong>{runtime.data?.maxFiles || "—"}</strong>
                <span>{t("settings.maxFiles")}</span>
              </div>
              <div>
                <strong>
                  {runtime.data
                    ? `${Math.round(runtime.data.maxFileBytes / 1024 / 1024)} MB`
                    : "—"}
                </strong>
                <span>{t("settings.maxFileSize")}</span>
              </div>
              <div>
                <strong>{runtime.data?.analyzerVersion || "—"}</strong>
                <span>{t("settings.analyzerVersion")}</span>
              </div>
            </div>
          </section>

          <section className="surface path-override-card">
            <header className="section-header">
              <div>
                <span className="eyebrow">{t("settings.overridesEyebrow")}</span>
                <h2>{t("settings.overridesTitle")}</h2>
              </div>
              {overrideSaved ? (
                <span className="saved-inline">
                  <CheckCircle2 size={14} />
                  {t("common.saved")}
                </span>
              ) : null}
            </header>
            <p className="settings-note">{t("settings.overridesDescription")}</p>
            <div className="override-list">
              {pathOverrides.length === 0 ? (
                <span className="section-empty">{t("settings.noOverrides")}</span>
              ) : (
                pathOverrides.map((override, index) => (
                  <div className="override-row" key={`${override.relativePath}-${index}`}>
                    <code>{override.relativePath}</code>
                    <select
                      value={override.role}
                      onChange={(event) => {
                        const next = [...pathOverrides];
                        next[index] = { ...override, role: event.target.value };
                        setPathOverrides(next);
                      }}
                    >
                      {pathRoles.map((role) => (
                        <option key={role} value={role}>
                          {t(`pathRole.${role}`, { defaultValue: role })}
                        </option>
                      ))}
                    </select>
                    <button
                      className="icon-button"
                      aria-label={t("settings.removeOverride")}
                      onClick={() =>
                        setPathOverrides(pathOverrides.filter((_, itemIndex) => itemIndex !== index))
                      }
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))
              )}
            </div>
            <div className="override-add">
              <input
                value={newPath}
                onChange={(event) => setNewPath(event.target.value)}
                placeholder={t("settings.overridePathPlaceholder")}
                aria-label={t("settings.overridePath")}
              />
              <select
                value={newRole}
                onChange={(event) => setNewRole(event.target.value)}
                aria-label={t("settings.overrideRole")}
              >
                {pathRoles.map((role) => (
                  <option key={role} value={role}>
                    {t(`pathRole.${role}`, { defaultValue: role })}
                  </option>
                ))}
              </select>
              <button
                className="button button-ghost button-small"
                onClick={addOverride}
                disabled={!newPath.trim()}
                type="button"
              >
                <Plus size={14} />
                {t("common.add")}
              </button>
            </div>
            {saveOverrides.error ? <ErrorPanel error={saveOverrides.error} /> : null}
            <button
              className="button button-secondary"
              onClick={() => saveOverrides.mutate()}
              disabled={saveOverrides.isPending}
            >
              <Save size={15} />
              {saveOverrides.isPending
                ? t("common.saving")
                : t("settings.saveOverrides")}
            </button>
          </section>
        </div>
      </div>
    </>
  );
}

function toLines(value: string): string[] {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}
