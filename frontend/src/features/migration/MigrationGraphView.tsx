import { Network } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import EmptyState from "../../components/EmptyState";
import StatusBadge from "../../components/StatusBadge";
import type { MigrationGraph } from "../../lib/types";
import RelationCanvas from "../relations/RelationCanvas";
import { layoutRelationGraph, type LayoutNode } from "../relations/relationLayout";
import { toRelationGraph } from "./migrationGraph";

interface MigrationGraphViewProps {
  graph: MigrationGraph;
  onSelectItem: (itemId: string) => void;
}

export default function MigrationGraphView({
  graph,
  onSelectItem
}: MigrationGraphViewProps) {
  const { t } = useTranslation();
  const [hover, setHover] = useState<LayoutNode | null>(null);
  const relationGraph = useMemo(() => toRelationGraph(graph), [graph]);
  const layout = useMemo(() => layoutRelationGraph(relationGraph), [relationGraph]);
  const hovered = hover
    ? graph.nodes.find((node) => node.id === hover.id)
    : undefined;

  if (layout.nodes.length === 0) {
    return (
      <EmptyState
        icon={Network}
        title={t("migration.graphEmptyTitle")}
        description={t("migration.graphEmptyDescription")}
      />
    );
  }

  return (
    <div className="migration-graph-view">
      {graph.truncated ? (
        <p className="migration-graph-truncated">
          {t("migration.graphTruncated", {
            items: graph.itemLimit,
            edges: graph.edgeLimit
          })}
        </p>
      ) : null}
      <div className="relation-map migration-map" aria-label={t("migration.graphTitle")}>
        <RelationCanvas
          layout={layout}
          ariaLabel={t("migration.graphTitle")}
          fitLabel={t("migration.fitGraph")}
          onNodeClick={onSelectItem}
          onHoverChange={setHover}
        />
        {hovered ? (
          <div className="relation-map-hover migration-map-hover" role="status">
            <strong>{hovered.title}</strong>
            <code>{hovered.filePath || hovered.title}</code>
            <span>
              {t(`migration.itemTypes.${hovered.itemType}`, {
                defaultValue: hovered.itemType
              })}
            </span>
            <StatusBadge value={hovered.status} kind="migration" />
          </div>
        ) : null}
      </div>
      <div className="migration-graph-legend">
        <span>
          <span className="migration-legend-line" aria-hidden="true" />
          {t("migration.graphLegend")}
        </span>
        <span className="migration-graph-counts">
          {t("migration.graphCounts", {
            items: graph.itemCount,
            edges: graph.edgeCount
          })}
        </span>
      </div>
    </div>
  );
}
