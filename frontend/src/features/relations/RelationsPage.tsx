import { useQuery } from "@tanstack/react-query";
import {
  Background,
  Controls,
  MarkerType,
  ReactFlow,
  type Edge,
  type Node
} from "@xyflow/react";
import type { TFunction } from "i18next";
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
                <RelationMap
                  projectId={projectId}
                  graph={graph.data}
                  onNodeClick={(fileId) =>
                    navigate(`/projects/${projectId}/inventory?fileId=${fileId}`)
                  }
                />
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
  const { nodes, edges } = useMemo(() => layoutGraph(graph, t), [graph, t]);
  return (
    <div className="relation-map" aria-label={t("relations.mapLabel")}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        fitView
        minZoom={0.2}
        maxZoom={1.5}
        nodesDraggable={false}
        nodesConnectable={false}
        onNodeClick={(_, node) => onNodeClick(node.id)}
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={20} size={1} color="#ded9e6" />
        <Controls showInteractive={false} />
      </ReactFlow>
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

function layoutGraph(
  graph: RelationGraph,
  t: TFunction
): { nodes: Node[]; edges: Edge[] } {
  const outgoing = new Map<string, string[]>();
  const remainingIndegree = new Map<string, number>();
  graph.nodes.forEach((node) => remainingIndegree.set(node.id, 0));
  graph.edges.forEach((edge) => {
    outgoing.set(edge.source, [...(outgoing.get(edge.source) || []), edge.target]);
    remainingIndegree.set(
      edge.target,
      (remainingIndegree.get(edge.target) || 0) + 1
    );
  });

  const depth = new Map<string, number>();
  const queue = graph.nodes
    .filter((node) => (remainingIndegree.get(node.id) || 0) === 0)
    .map((node) => node.id);
  queue.forEach((id) => depth.set(id, 0));
  for (let index = 0; index < queue.length; index++) {
    const source = queue[index];
    const sourceDepth = depth.get(source) || 0;
    for (const target of outgoing.get(source) || []) {
      depth.set(target, Math.max(depth.get(target) || 0, sourceDepth + 1));
      const nextIndegree = (remainingIndegree.get(target) || 1) - 1;
      remainingIndegree.set(target, nextIndegree);
      if (nextIndegree === 0) {
        queue.push(target);
      }
    }
  }
  let cycleLayer = 0;
  graph.nodes.forEach((node) => {
    if (!depth.has(node.id)) {
      depth.set(node.id, cycleLayer % 3);
      cycleLayer++;
    }
  });

  const layers = new Map<number, typeof graph.nodes>();
  graph.nodes.forEach((node) => {
    const value = depth.get(node.id) || 0;
    layers.set(value, [...(layers.get(value) || []), node]);
  });

  const flowNodes: Node[] = [];
  [...layers.entries()]
    .sort((left, right) => left[0] - right[0])
    .forEach(([layer, layerNodes]) => {
      layerNodes.forEach((node, index) => {
        const inferred = graph.edges.some(
          (edge) =>
            (edge.source === node.id || edge.target === node.id) &&
            edge.certainties.includes("INFERRED")
        );
        flowNodes.push({
          id: node.id,
          position: { x: layer * 280, y: index * 125 },
          data: {
            label: (
              <div className="relation-flow-label">
                <strong>{node.fileName}</strong>
                <code>{node.path}</code>
                <span>
                  {t("relations.nodeCounts", {
                    incoming: node.incoming,
                    outgoing: node.outgoing
                  })}
                </span>
              </div>
            ),
            certainty: inferred ? "INFERRED" : "ANALYZED"
          },
          style: {
            width: 220,
            padding: 0,
            border: inferred ? "1px solid #e5cfaa" : "1px solid #dcd5f4",
            borderRadius: 9,
            background: inferred ? "#fff9ed" : "#faf8ff",
            boxShadow: "0 6px 18px rgba(25, 23, 31, 0.06)"
          }
        });
      });
    });

  const flowEdges: Edge[] = graph.edges.map((edge) => {
    const inferred = edge.certainties.includes("INFERRED");
    return {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: "smoothstep",
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color: inferred ? "#b2782c" : "#6650d9"
      },
      style: {
        stroke: inferred ? "#c3954d" : "#8572e8",
        strokeWidth: edge.count > 1 ? 1.8 : 1.2
      },
      label: edge.count > 1 ? String(edge.count) : undefined,
      labelStyle: { fontSize: 9, fill: "#696474" },
      labelBgStyle: { fill: "#ffffff", fillOpacity: 0.9 }
    };
  });
  return { nodes: flowNodes, edges: flowEdges };
}

function fileName(path: string): string {
  const segments = path.split("/");
  return segments[segments.length - 1] || path;
}
