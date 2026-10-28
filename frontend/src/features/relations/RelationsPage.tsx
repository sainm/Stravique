import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  FileCode2,
  Filter,
  GitCompareArrows,
  Network,
  RefreshCw,
  Search
} from "lucide-react";
import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import EmptyState from "../../components/EmptyState";
import ErrorPanel from "../../components/ErrorPanel";
import LoadingBlock from "../../components/LoadingBlock";
import PageHeader from "../../components/PageHeader";
import ScanButton from "../../components/ScanButton";
import StatusBadge from "../../components/StatusBadge";
import { api } from "../../lib/api";
import { formatNumber } from "../../lib/format";
import type { AnalysisRelation, RelationGraph } from "../../lib/types";
import RelationCanvas from "./RelationCanvas";
import { layoutRelationGraph, type LayoutNode } from "./relationLayout";

const relationTypes = [
  "JAVA_IMPORT",
  "JAVA_INHERITANCE",
  "SEASAR_BINDING",
  "SEASAR_DICON_INJECTION",
  "CONFIG_CLASS_REFERENCE",
  "STRUTS_ACTION_TO_JSP",
  "SASTRUTS_ACTION_TO_JSP",
  "JSP_FORM_TO_ACTION",
  "DAO_SQL_REFERENCE",
  "MESSAGE_PRODUCTION",
  "MESSAGE_DISPLAY"
];

export default function RelationsPage() {
  const { projectId = "" } = useParams();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const relationType = params.get("type") || "";
  const certainty = params.get("certainty") || "";
  const view = params.get("view") === "map" ? "map" : "table";
  const [searchText, setSearchText] = useState(params.get("q") || "");
  const query = useDeferredValue(searchText);
  const [page, setPage] = useState(0);

  const project = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => api.project(projectId)
  });
  const overview = useQuery({
    queryKey: ["relation-overview", projectId],
    queryFn: () => api.relationOverview(projectId)
  });
  const relations = useQuery({
    queryKey: ["relations", projectId, overview.data?.scanId, relationType, certainty, query, page],
    queryFn: () =>
      api.relations(projectId, {
        scanId: overview.data?.scanId,
        relationType,
        certainty,
        q: query,
        page,
        size: 60
      }),
    enabled: Boolean(overview.data?.scanId) && view === "table"
  });
  const graph = useQuery({
    queryKey: [
      "relation-graph",
      projectId,
      overview.data?.scanId,
      relationType,
      certainty,
      query
    ],
    queryFn: () =>
      api.relationGraph(projectId, {
        scanId: overview.data?.scanId,
        relationType,
        certainty,
        q: query
      }),
    enabled: Boolean(overview.data?.scanId) && view === "map"
  });

  useEffect(() => {
    setPage(0);
  }, [relationType, certainty, query]);

  const setFilter = (key: string, value?: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setParams(next, { replace: true });
  };

  const setView = (nextView: "table" | "map") => {
    const next = new URLSearchParams(params);
    if (nextView === "table") {
      next.delete("view");
    } else {
      next.set("view", "map");
    }
    setParams(next, { replace: true });
  };

  return (
    <>
      <PageHeader
        eyebrow={t("relations.eyebrow")}
        title={t("relations.title")}
        description={t("relations.description")}
        actions={
          <div className="header-action-group">
            <button
              className="button button-ghost button-small"
              onClick={() => {
                overview.refetch();
                if (view === "map") {
                  graph.refetch();
                } else {
                  relations.refetch();
                }
              }}
            >
              <RefreshCw size={14} />
              {t("common.refresh")}
            </button>
            <ScanButton
              projectId={projectId}
              active={["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(
                project.data?.latestScan?.status || ""
              )}
              compact
            />
          </div>
        }
      />

      {!project.data?.latestScan && !project.isLoading ? (
        <section className="surface">
          <EmptyState
            icon={Network}
            title={t("relations.noScanTitle")}
            description={t("relations.noScanDescription")}
            action={<ScanButton projectId={projectId} />}
          />
        </section>
      ) : null}

      {overview.isLoading || relations.isLoading || graph.isLoading ? <LoadingBlock /> : null}
      {overview.error ? (
        <ErrorPanel error={overview.error} onRetry={() => overview.refetch()} />
      ) : null}
      {relations.error ? (
        <ErrorPanel error={relations.error} onRetry={() => relations.refetch()} />
      ) : null}
      {graph.error ? (
        <ErrorPanel error={graph.error} onRetry={() => graph.refetch()} />
      ) : null}

      {overview.data?.scanId ? (
        <>
          <section className="relation-summary surface">
            <div className="relation-summary-lead">
              <div className="summary-icon">
                <GitCompareArrows size={20} />
              </div>
              <div>
                <strong>
                  {t("relations.total", {
                    count: formatNumber(overview.data.total, i18n.language)
                  })}
                </strong>
                <p>{t("relations.totalDescription")}</p>
              </div>
            </div>
            <div className="relation-type-strip">
              <button
                className={!relationType ? "relation-filter-active" : ""}
                onClick={() => setFilter("type", undefined)}
              >
                {t("relations.allTypes")}
                <strong>{overview.data.total}</strong>
              </button>
              {Object.entries(overview.data.byType)
                .sort((left, right) => right[1] - left[1])
                .map(([type, count]) => (
                  <button
                    key={type}
                    className={relationType === type ? "relation-filter-active" : ""}
                    onClick={() => setFilter("type", type)}
                  >
                    {t(`relation.${type}`, { defaultValue: type })}
                    <strong>{count}</strong>
                  </button>
                ))}
            </div>
          </section>

          <section className="surface relation-workspace">
            <header className="relation-toolbar">
              <div className="mode-switch relation-view-switch">
                <button
                  className={view === "table" ? "mode-active" : ""}
                  onClick={() => setView("table")}
                >
                  {t("relations.tableView")}
                </button>
                <button
                  className={view === "map" ? "mode-active" : ""}
                  onClick={() => setView("map")}
                >
                  {t("relations.mapView")}
                </button>
              </div>
              <label className="search-field">
                <Search size={15} />
                <span className="sr-only">{t("relations.search")}</span>
                <input
                  value={searchText}
                  onChange={(event) => {
                    setSearchText(event.target.value);
                    setFilter("q", event.target.value || undefined);
                  }}
                  placeholder={t("relations.searchPlaceholder")}
                />
              </label>
              <label className="select-field">
                <Filter size={14} />
                <span className="sr-only">{t("relations.typeFilter")}</span>
                <select
                  value={relationType}
                  onChange={(event) => setFilter("type", event.target.value || undefined)}
                >
                  <option value="">{t("relations.allTypes")}</option>
                  {relationTypes.map((type) => (
                    <option key={type} value={type}>
                      {t(`relation.${type}`, { defaultValue: type })}
                    </option>
                  ))}
                </select>
              </label>
              <label className="select-field">
                <GitCompareArrows size={14} />
                <span className="sr-only">{t("relations.certaintyFilter")}</span>
                <select
                  value={certainty}
                  onChange={(event) =>
                    setFilter("certainty", event.target.value || undefined)
                  }
                >
                  <option value="">{t("relations.allCertainties")}</option>
                  <option value="ANALYZED">
                    {t("status.certainty.ANALYZED")}
                  </option>
                  <option value="INFERRED">
                    {t("status.certainty.INFERRED")}
                  </option>
                  <option value="REVIEW_REQUIRED">
                    {t("status.certainty.REVIEW_REQUIRED")}
                  </option>
                </select>
              </label>
              <span className="relation-count">
                {t("relations.matches", {
                  count:
                    view === "map"
                      ? graph.data?.relationCount || 0
                      : relations.data?.totalElements || 0
                })}
              </span>
            </header>

            {view === "map" ? (
              graph.data && graph.data.nodes.length > 0 ? (
                <>
                  {graph.data.truncated ? (
                    <p className="relation-map-truncated">
                      {t("relations.graphTruncated", {
                        limit: formatNumber(graph.data.limit ?? 0, i18n.language)
                      })}
                    </p>
                  ) : null}
                  <RelationMap
                    projectId={projectId}
                    graph={graph.data}
                    onNodeClick={(fileId) =>
                      navigate(`/projects/${projectId}/inventory?fileId=${fileId}`)
                    }
                  />
                </>
              ) : (
                <EmptyState
                  icon={Network}
                  title={t("relations.emptyTitle")}
                  description={t("relations.emptyDescription")}
                />
              )
            ) : relations.data?.content.length === 0 ? (
                <EmptyState
                  icon={Network}
                  title={t("relations.emptyTitle")}
                  description={t("relations.emptyDescription")}
                />
            ) : (
              <div className="relation-table">
                <div className="relation-table-head" aria-hidden="true">
                  <span>{t("relations.columns.type")}</span>
                  <span>{t("relations.columns.source")}</span>
                  <span>{t("relations.columns.target")}</span>
                  <span>{t("relations.columns.evidence")}</span>
                  <span>{t("relations.columns.certainty")}</span>
                </div>
                {relations.data?.content.map((relation) => (
                  <RelationRow
                    key={relation.id}
                    projectId={projectId}
                    relation={relation}
                  />
                ))}
              </div>
            )}

            {view === "table" && (relations.data?.totalPages || 0) > 1 ? (
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
                    total: relations.data?.totalPages || 1
                  })}
                </span>
                <button
                  className="button button-ghost button-small"
                  disabled={page >= (relations.data?.totalPages || 1) - 1}
                  onClick={() => setPage(page + 1)}
                >
                  {t("common.next")}
                </button>
              </footer>
            ) : null}
          </section>
        </>
      ) : null}
    </>
  );
}

function RelationRow({
  projectId,
  relation
}: {
  projectId: string;
  relation: AnalysisRelation;
}) {
  const { t } = useTranslation();
  return (
    <article className="relation-table-row">
      <div className="relation-kind">
        <GitCompareArrows size={14} />
        <strong>{t(`relation.${relation.relationType}`, {
          defaultValue: relation.relationType
        })}</strong>
      </div>
      <Link
        className="relation-file-link"
        to={`/projects/${projectId}/inventory?fileId=${relation.sourceFileId}`}
        title={relation.sourceFilePath}
      >
        <FileCode2 size={14} />
        <span>{relation.sourceObjectKey}</span>
        <small>{fileName(relation.sourceFilePath)}</small>
      </Link>
      <Link
        className="relation-file-link"
        to={`/projects/${projectId}/inventory?fileId=${relation.targetFileId}`}
        title={relation.targetFilePath}
      >
        <ArrowRight size={14} />
        <span>{relation.targetObjectKey}</span>
        <small>{fileName(relation.targetFilePath)}</small>
      </Link>
      <div className="relation-evidence">
        <code>{relation.evidencePath}</code>
        <span>{relation.lineStart ? `:${relation.lineStart}` : ""}</span>
      </div>
      <StatusBadge value={relation.certainty} kind="certainty" />
    </article>
  );
}

function RelationMap({
  projectId,
  graph,
  onNodeClick
}: {
  projectId: string;
  graph: RelationGraph;
  onNodeClick: (fileId: string) => void;
}) {
  const { t } = useTranslation();
  const layout = useMemo(() => layoutRelationGraph(graph), [graph]);
  const [hovered, setHovered] = useState<LayoutNode | null>(null);
  return (
    <div className="relation-map" aria-label={t("relations.mapLabel")}>
      <RelationCanvas
        layout={layout}
        ariaLabel={t("relations.mapLabel")}
        fitLabel={t("relations.fitGraph")}
        onNodeClick={onNodeClick}
        onHoverChange={setHovered}
      />
      {hovered ? (
        <div className="relation-map-hover" role="status">
          <strong>{hovered.fileName}</strong>
          <code>{hovered.path}</code>
          <span>
            {t("relations.nodeCounts", {
              incoming: hovered.incoming,
              outgoing: hovered.outgoing
            })}
          </span>
        </div>
      ) : null}
      <div className="relation-map-note">
        <span>{t("relations.mapHint")}</span>
        <Link to={`/projects/${projectId}/inventory`}>
          {t("relations.openInventory")}
          <ArrowRight size={13} />
        </Link>
      </div>
    </div>
  );
}

function fileName(path: string): string {
  const segments = path.split("/");
  return segments[segments.length - 1] || path;
}
