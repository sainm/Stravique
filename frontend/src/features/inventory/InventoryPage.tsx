import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  Code2,
  File,
  FileCode2,
  FileSearch2,
  Files,
  Filter,
  Folder,
  FolderOpen,
  RefreshCw,
  Search,
  ShieldAlert,
  X
} from "lucide-react";
import {
  useDeferredValue,
  useEffect,
  useMemo,
  useState
} from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams, useSearchParams } from "react-router-dom";
import EmptyState from "../../components/EmptyState";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import PageHeader from "../../components/PageHeader";
import ScanButton from "../../components/ScanButton";
import StatusBadge from "../../components/StatusBadge";
import { api } from "../../lib/api";
import { formatBytes, formatDate, formatNumber } from "../../lib/format";
import type {
  DirectoryNode,
  FileDetail,
  Finding,
  PageResponse,
  ProjectFile
} from "../../lib/types";

const fileTypes = [
  "JAVA",
  "TEST",
  "JSP",
  "HTML",
  "JAVASCRIPT",
  "CSS",
  "XML",
  "PROPERTIES",
  "YAML",
  "DICON",
  "SQL",
  "TLD",
  "MAVEN_POM",
  "GRADLE"
];

const fileStatuses = ["ANALYZED", "PARSE_FAILED", "SKIPPED"];

export default function InventoryPage() {
  const { projectId = "" } = useParams();
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedFileId = searchParams.get("fileId") || undefined;
  const reviewMode = searchParams.get("review") === "true";
  const [pathPrefix, setPathPrefix] = useState("");
  const [searchText, setSearchText] = useState("");
  const search = useDeferredValue(searchText);
  const [fileType, setFileType] = useState("");
  const [scanStatus, setScanStatus] = useState("");
  const [page, setPage] = useState(0);

  const project = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => api.project(projectId),
    refetchInterval: (query) =>
      ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(
        query.state.data?.latestScan?.status || ""
      )
        ? 900
        : false
  });
  const tree = useQuery({
    queryKey: ["tree", projectId],
    queryFn: () => api.tree(projectId)
  });
  const files = useQuery({
    queryKey: [
      "files",
      projectId,
      tree.data?.scanId,
      search,
      fileType,
      scanStatus,
      pathPrefix,
      page
    ],
    queryFn: () =>
      api.files(projectId, {
        scanId: tree.data?.scanId,
        q: search,
        fileType,
        scanStatus,
        pathPrefix,
        page,
        size: 80
      }),
    enabled: Boolean(tree.data?.scanId) && !reviewMode
  });
  const reviews = useQuery({
    queryKey: ["reviews", projectId, tree.data?.scanId],
    queryFn: () => api.reviews(projectId, tree.data?.scanId, 0, 200),
    enabled: Boolean(tree.data?.scanId) && reviewMode
  });

  useEffect(() => {
    setPage(0);
  }, [search, fileType, scanStatus, pathPrefix, reviewMode]);

  const updateParam = (key: string, value?: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setSearchParams(next, { replace: true });
  };

  const activeScan = ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(
    project.data?.latestScan?.status || ""
  );

  return (
    <div className={reviewMode ? "inventory-page review-mode" : "inventory-page"}>
      <PageHeader
        eyebrow={t("inventory.eyebrow")}
        title={t("inventory.title")}
        description={t("inventory.description")}
        actions={
          <div className="header-action-group">
            <Link
              to={`/projects/${projectId}/overview`}
              className="button button-ghost button-small"
            >
              {t("inventory.scanOverview")}
            </Link>
            <ScanButton projectId={projectId} active={activeScan} compact />
          </div>
        }
      />

      {!project.data?.latestScan && !project.isLoading ? (
        <section className="surface">
          <EmptyState
            icon={FileSearch2}
            title={t("inventory.noScanTitle")}
            description={t("inventory.noScanDescription")}
            action={<ScanButton projectId={projectId} />}
          />
        </section>
      ) : null}

      {tree.error ? (
        <ErrorPanel error={tree.error} onRetry={() => tree.refetch()} />
      ) : null}
      {tree.isLoading ? <LoadingBlock /> : null}

      {tree.data?.scanId ? (
        <>
          <div className="explorer-toolbar">
            <div className="mode-switch" role="group" aria-label={t("inventory.viewLabel")}>
              <button
                className={!reviewMode ? "mode-active" : ""}
                onClick={() => {
                  const next = new URLSearchParams(searchParams);
                  next.delete("review");
                  setSearchParams(next, { replace: true });
                }}
              >
                <Files size={15} />
                {t("inventory.filesTab", {
                  count: project.data?.latestScan?.fileCount || 0
                })}
              </button>
              <button
                className={reviewMode ? "mode-active" : ""}
                onClick={() => {
                  const next = new URLSearchParams(searchParams);
                  next.set("review", "true");
                  setSearchParams(next, { replace: true });
                }}
              >
                <ShieldAlert size={15} />
                {t("inventory.reviewTab", {
                  count: project.data?.latestScan?.reviewCount || 0
                })}
              </button>
            </div>
            <div className="toolbar-meta">
              <StatusBadge
                value={project.data?.latestScan?.status}
                kind="scan"
              />
              <span>{t("inventory.analyzerVersion", {
                version: project.data?.latestScan?.analyzerVersion || "—"
              })}</span>
              <button
                className="icon-button"
                aria-label={t("common.refresh")}
                title={t("common.refresh")}
                onClick={() => {
                  tree.refetch();
                  files.refetch();
                  reviews.refetch();
                }}
              >
                <RefreshCw size={15} />
              </button>
            </div>
          </div>

          <div className="explorer-grid">
            <aside className="explorer-tree surface">
              <header className="explorer-pane-header">
                <div>
                  <span className="eyebrow">{t("inventory.treeEyebrow")}</span>
                  <h2>{t("inventory.treeTitle")}</h2>
                </div>
                <span>{tree.data.directories.length}</span>
              </header>
              <button
                className={`tree-root ${pathPrefix === "" ? "tree-active" : ""}`}
                onClick={() => setPathPrefix("")}
              >
                <FolderOpen size={16} />
                <span>{t("inventory.allFiles")}</span>
                <small>{project.data?.latestScan?.fileCount || 0}</small>
              </button>
              <div className="tree-scroll">
                {tree.data.directories.map((node) => (
                  <DirectoryTree
                    key={node.path}
                    node={node}
                    selected={pathPrefix}
                    onSelect={setPathPrefix}
                  />
                ))}
              </div>
              <div className="module-summary">
                <span className="rail-label">{t("inventory.modules")}</span>
                {tree.data.modules.map((module) => (
                  <div key={module.id}>
                    <span>{module.name}</span>
                    <small>
                      {t("inventory.moduleSummary", {
                        roots: module.sourceRoots.length + module.webRoots.length,
                        dependencies: tree.data.moduleDependencies.filter(
                          (dependency) =>
                            dependency.fromModulePath === module.relativePath
                        ).length
                      })}
                    </small>
                  </div>
                ))}
              </div>
            </aside>

            <section className="explorer-results surface">
              <header className="results-header">
                <div>
                  <span className="eyebrow">
                    {reviewMode ? t("inventory.reviewEyebrow") : t("inventory.filesEyebrow")}
                  </span>
                  <h2>
                    {reviewMode
                      ? t("inventory.reviewResults", {
                          count: reviews.data?.totalElements || 0
                        })
                      : t("inventory.fileResults", {
                          count: files.data?.totalElements || 0
                        })}
                  </h2>
                </div>
                <span className="current-prefix">
                  {pathPrefix || t("inventory.allFiles")}
                </span>
              </header>

              <label className="mobile-directory-select">
                <Folder size={14} />
                <span className="sr-only">{t("inventory.treeTitle")}</span>
                <select
                  value={pathPrefix}
                  onChange={(event) => setPathPrefix(event.target.value)}
                  aria-label={t("inventory.treeTitle")}
                >
                  <option value="">{t("inventory.allFiles")}</option>
                  {flattenDirectories(tree.data.directories).map((node) => (
                    <option key={node.path} value={node.path}>
                      {node.path} ({node.fileCount})
                    </option>
                  ))}
                </select>
              </label>

              {!reviewMode ? (
                <>
                  <div className="file-filters">
                    <label className="search-field">
                      <Search size={15} />
                      <span className="sr-only">{t("inventory.search")}</span>
                      <input
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                        placeholder={t("inventory.searchPlaceholder")}
                      />
                    </label>
                    <label className="select-field">
                      <Filter size={14} />
                      <span className="sr-only">{t("inventory.fileType")}</span>
                      <select
                        value={fileType}
                        onChange={(event) => setFileType(event.target.value)}
                      >
                        <option value="">{t("inventory.allTypes")}</option>
                        {fileTypes.map((type) => (
                          <option key={type} value={type}>
                            {t(`fileType.${type}`, { defaultValue: type })}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="select-field">
                      <ShieldAlert size={14} />
                      <span className="sr-only">{t("inventory.parseStatus")}</span>
                      <select
                        value={scanStatus}
                        onChange={(event) => setScanStatus(event.target.value)}
                      >
                        <option value="">{t("inventory.allStates")}</option>
                        {fileStatuses.map((status) => (
                          <option key={status} value={status}>
                            {t(`status.file.${status}`, { defaultValue: status })}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                  {files.isLoading ? <LoadingBlock /> : null}
                  {files.error ? (
                    <ErrorPanel error={files.error} onRetry={() => files.refetch()} />
                  ) : null}
                  {files.data?.content.length === 0 ? (
                    <EmptyState
                      icon={FileSearch2}
                      title={t("inventory.noMatchesTitle")}
                      description={t("inventory.noMatchesDescription")}
                    />
                  ) : (
                    <FileList
                      data={files.data}
                      selectedFileId={selectedFileId}
                      onSelect={(id) => updateParam("fileId", id)}
                    />
                  )}
                  <Pagination
                    page={files.data?.page || 0}
                    totalPages={files.data?.totalPages || 0}
                    onPage={setPage}
                  />
                </>
              ) : (
                <>
                  {reviews.isLoading ? <LoadingBlock /> : null}
                  {reviews.error ? (
                    <ErrorPanel error={reviews.error} onRetry={() => reviews.refetch()} />
                  ) : null}
                  {reviews.data?.content.length === 0 ? (
                    <EmptyState
                      icon={ShieldAlert}
                      title={t("inventory.noReviewsTitle")}
                      description={t("inventory.noReviewsDescription")}
                    />
                  ) : (
                    <div className="review-results">
                      {reviews.data?.content.map((finding) => (
                        <button
                          key={finding.id}
                          className={`review-result-row ${
                            selectedFileId === finding.fileId ? "result-selected" : ""
                          }`}
                          onClick={() => updateParam("fileId", finding.fileId || undefined)}
                          disabled={!finding.fileId}
                        >
                          <div className="review-result-icon">
                            <AlertTriangle size={16} />
                          </div>
                          <div className="review-result-copy">
                            <strong>{finding.summary}</strong>
                            <code>
                              {finding.filePath}
                              {finding.lineStart ? `:${finding.lineStart}` : ""}
                            </code>
                          </div>
                          <StatusBadge value={finding.certainty} kind="certainty" />
                          <ChevronRight size={15} />
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )}
            </section>

            <FileInspector
              projectId={projectId}
              fileId={selectedFileId}
              onClose={() => updateParam("fileId", undefined)}
            />
          </div>
        </>
      ) : null}
    </div>
  );
}

function DirectoryTree({
  node,
  selected,
  onSelect
}: {
  node: DirectoryNode;
  selected: string;
  onSelect: (path: string) => void;
}) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(node.path.split("/").length < 3);
  return (
    <div className="tree-node">
      <div className={`tree-row ${selected === node.path ? "tree-active" : ""}`}>
        <button
          className="tree-toggle"
          onClick={() => setExpanded(!expanded)}
          disabled={node.children.length === 0}
          aria-label={expanded ? t("inventory.collapse") : t("inventory.expand")}
        >
          {node.children.length > 0 ? (
            expanded ? (
              <ChevronDown size={13} />
            ) : (
              <ChevronRight size={13} />
            )
          ) : (
            <span className="tree-spacer" />
          )}
        </button>
        <button className="tree-label" onClick={() => onSelect(node.path)}>
          {expanded ? <FolderOpen size={15} /> : <Folder size={15} />}
          <span>{node.name}</span>
          <small>{node.fileCount}</small>
        </button>
      </div>
      {expanded && node.children.length > 0 ? (
        <div className="tree-children">
          {node.children.map((child) => (
            <DirectoryTree
              key={child.path}
              node={child}
              selected={selected}
              onSelect={onSelect}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function FileList({
  data,
  selectedFileId,
  onSelect
}: {
  data?: PageResponse<ProjectFile>;
  selectedFileId?: string;
  onSelect: (id: string) => void;
}) {
  const { t, i18n } = useTranslation();
  return (
    <div className="file-list">
      {data?.content.map((file) => (
        <button
          key={file.id}
          className={`file-row ${selectedFileId === file.id ? "result-selected" : ""}`}
          onClick={() => onSelect(file.id)}
        >
          <span className={`file-type-icon file-${file.fileType.toLowerCase()}`}>
            {file.fileType === "JAVA" || file.fileType === "TEST" ? (
              <FileCode2 size={16} />
            ) : (
              <File size={16} />
            )}
          </span>
          <span className="file-row-copy">
            <strong>{file.fileName}</strong>
            <code>{file.relativePath}</code>
          </span>
          <span className="file-row-meta">
            <span>
              {t(`fileType.${file.fileType}`, { defaultValue: file.fileType })}
            </span>
            <span>{formatBytes(file.sizeBytes, i18n.language)}</span>
          </span>
          <StatusBadge value={file.scanStatus} kind="file" />
          {file.frameworkApiCount > 0 ? (
            <span className="api-count">
              {t("inventory.apiCount", { count: file.frameworkApiCount })}
            </span>
          ) : (
            <span />
          )}
          <ChevronRight size={15} />
        </button>
      ))}
    </div>
  );
}

function Pagination({
  page,
  totalPages,
  onPage
}: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  const { t } = useTranslation();
  if (totalPages <= 1) {
    return null;
  }
  return (
    <footer className="pagination">
      <button
        className="button button-ghost button-small"
        onClick={() => onPage(Math.max(0, page - 1))}
        disabled={page === 0}
      >
        {t("common.previous")}
      </button>
      <span>{t("common.pageOf", { page: page + 1, total: totalPages })}</span>
      <button
        className="button button-ghost button-small"
        onClick={() => onPage(Math.min(totalPages - 1, page + 1))}
        disabled={page >= totalPages - 1}
      >
        {t("common.next")}
      </button>
    </footer>
  );
}

function FileInspector({
  projectId,
  fileId,
  onClose
}: {
  projectId: string;
  fileId?: string;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation();
  const detail = useQuery({
    queryKey: ["file", fileId],
    queryFn: () => api.file(fileId!),
    enabled: Boolean(fileId)
  });
  const grouped = useMemo(() => groupFindings(detail.data?.findings || []), [detail.data]);

  return (
    <aside className={`inspector surface ${fileId ? "inspector-open" : ""}`}>
      {!fileId ? (
        <div className="inspector-placeholder">
          <div className="empty-icon">
            <Code2 size={21} />
          </div>
          <h3>{t("inventory.inspectorTitle")}</h3>
          <p>{t("inventory.inspectorPlaceholder")}</p>
        </div>
      ) : detail.isLoading ? (
        <LoadingBlock />
      ) : detail.error ? (
        <ErrorPanel error={detail.error} onRetry={() => detail.refetch()} />
      ) : detail.data ? (
        <>
          <header className="inspector-header">
            <div>
              <span className="eyebrow">{t("inventory.inspectorEyebrow")}</span>
              <h2>{detail.data.file.fileName}</h2>
              <code>{detail.data.file.relativePath}</code>
            </div>
            <button className="icon-button" onClick={onClose} aria-label={t("common.close")}>
              <X size={16} />
            </button>
          </header>
          <div className="file-metadata">
            <div>
              <span>{t("inventory.metadata.type")}</span>
              <strong>{detail.data.file.fileType}</strong>
            </div>
            <div>
              <span>{t("inventory.metadata.size")}</span>
              <strong>{formatBytes(detail.data.file.sizeBytes, i18n.language)}</strong>
            </div>
            <div>
              <span>{t("inventory.metadata.encoding")}</span>
              <strong>{detail.data.file.encoding || "—"}</strong>
            </div>
            <div>
              <span>{t("inventory.metadata.modified")}</span>
              <strong>{formatDate(detail.data.file.lastModifiedAt, i18n.language)}</strong>
            </div>
            <div>
              <span>{t("inventory.metadata.change")}</span>
              <StatusBadge value={detail.data.file.changeType} kind="change" />
            </div>
            <div>
              <span>{t("inventory.metadata.role")}</span>
              <strong>
                {detail.data.file.structureRole
                  ? t(`pathRole.${detail.data.file.structureRole}`, {
                      defaultValue: detail.data.file.structureRole
                    })
                  : "—"}
              </strong>
            </div>
          </div>
          {detail.data.file.parseError ? (
            <div className="parse-error-box">
              <AlertTriangle size={16} />
              <div>
                <strong>{t("inventory.parseError")}</strong>
                <p>{detail.data.file.parseError}</p>
              </div>
            </div>
          ) : null}
          <div className="inspector-evidence-heading">
            <span className="eyebrow">{t("inventory.evidenceEyebrow")}</span>
            <h3>
              {t("inventory.evidenceTitle", {
                count: detail.data.findings.length
              })}
            </h3>
          </div>
          {grouped.length === 0 ? (
            <div className="inspector-empty">
              {t("inventory.noEvidence")}
            </div>
          ) : (
            <div className="evidence-list">
              {grouped.map((finding) => (
                <article className="evidence-card" key={finding.id}>
                  <header>
                    <div className="evidence-kind">
                      <span>{finding.analysisKind}</span>
                      <strong>{finding.objectKey}</strong>
                    </div>
                    <StatusBadge value={finding.certainty} kind="certainty" />
                  </header>
                  <p>{finding.summary}</p>
                  {finding.evidence ? (
                    <pre>
                      <span>
                        {finding.lineStart ? `L${finding.lineStart}` : ""}
                      </span>
                      <code>{finding.evidence}</code>
                    </pre>
                  ) : null}
                  <footer>
                    <code>{finding.ruleId}</code>
                    {finding.lineStart ? (
                      <span>
                        {t("inventory.line", { line: finding.lineStart })}
                      </span>
                    ) : null}
                    <span>{t("inventory.analyzer", { version: finding.analyzerVersion })}</span>
                    {isTechnologyKey(finding.objectKey) ? (
                      <Link
                        to={`/projects/${projectId}/frameworks?key=${finding.objectKey}`}
                      >
                        {t("inventory.openFramework")}
                      </Link>
                    ) : null}
                  </footer>
                </article>
              ))}
            </div>
          )}
        </>
      ) : null}
    </aside>
  );
}

function groupFindings(findings: Finding[]): Finding[] {
  const seen = new Set<string>();
  return findings.filter((finding) => {
    const key = `${finding.objectKey}:${finding.ruleId}:${finding.lineStart}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function isTechnologyKey(value: string): boolean {
  return [
    "JAVA",
    "MAVEN",
    "GRADLE",
    "SASTRUTS",
    "STRUTS1",
    "SEASAR2",
    "S2JDBC",
    "SERVLET",
    "JSP",
    "JSTL",
    "CUSTOM_TAGS",
    "JUNIT",
    "DB_ACCESS"
  ].includes(value);
}

function flattenDirectories(nodes: DirectoryNode[]): DirectoryNode[] {
  return nodes.flatMap((node) => [node, ...flattenDirectories(node.children)]);
}
