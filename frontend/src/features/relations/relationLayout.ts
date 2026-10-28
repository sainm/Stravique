import type { RelationGraph } from "../../lib/types";

export const COLUMN_WIDTH = 250;
export const ROW_HEIGHT = 58;
export const NODE_WIDTH = 190;
export const NODE_HEIGHT = 40;

export interface LayoutNode {
  id: string;
  path: string;
  fileName: string;
  fileType: string;
  incoming: number;
  outgoing: number;
  layer: number;
  row: number;
  x: number;
  y: number;
  inferred: boolean;
}

export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
  count: number;
  inferred: boolean;
}

export interface GraphLayout {
  nodes: LayoutNode[];
  edges: LayoutEdge[];
  width: number;
  height: number;
}

const EMPTY_LAYOUT: GraphLayout = { nodes: [], edges: [], width: 0, height: 0 };

export function layoutRelationGraph(graph: RelationGraph | undefined): GraphLayout {
  if (!graph || graph.nodes.length === 0) {
    return EMPTY_LAYOUT;
  }

  const outgoing = new Map<string, string[]>();
  const indegree = new Map<string, number>();
  const inferredNodes = new Set<string>();

  for (const node of graph.nodes) {
    indegree.set(node.id, 0);
  }
  for (const edge of graph.edges) {
    const successors = outgoing.get(edge.source);
    if (successors) {
      successors.push(edge.target);
    } else {
      outgoing.set(edge.source, [edge.target]);
    }
    indegree.set(edge.target, (indegree.get(edge.target) ?? 0) + 1);
    if (edge.certainties.includes("INFERRED")) {
      inferredNodes.add(edge.source);
      inferredNodes.add(edge.target);
    }
  }

  const layer = new Map<string, number>();
  const queue: string[] = [];
  for (const node of graph.nodes) {
    if ((indegree.get(node.id) ?? 0) === 0) {
      layer.set(node.id, 0);
      queue.push(node.id);
    }
  }
  const remaining = new Map(indegree);
  for (let index = 0; index < queue.length; index++) {
    const source = queue[index];
    const sourceLayer = layer.get(source) ?? 0;
    for (const target of outgoing.get(source) ?? []) {
      layer.set(target, Math.max(layer.get(target) ?? 0, sourceLayer + 1));
      const next = (remaining.get(target) ?? 1) - 1;
      remaining.set(target, next);
      if (next === 0) {
        queue.push(target);
      }
    }
  }

  let maxLayer = 0;
  for (const value of layer.values()) {
    maxLayer = Math.max(maxLayer, value);
  }
  const cycleLayer = maxLayer + 1;
  for (const node of graph.nodes) {
    if (!layer.has(node.id)) {
      layer.set(node.id, cycleLayer);
    }
  }

  const buckets = new Map<number, LayoutNode[]>();
  let widestRow = 0;
  for (const node of graph.nodes) {
    const nodeLayer = layer.get(node.id) ?? 0;
    const bucket = buckets.get(nodeLayer);
    const row = bucket ? bucket.length : 0;
    const placed: LayoutNode = {
      id: node.id,
      path: node.path,
      fileName: node.fileName,
      fileType: node.fileType,
      incoming: node.incoming,
      outgoing: node.outgoing,
      layer: nodeLayer,
      row,
      x: nodeLayer * COLUMN_WIDTH,
      y: row * ROW_HEIGHT,
      inferred: inferredNodes.has(node.id)
    };
    if (bucket) {
      bucket.push(placed);
    } else {
      buckets.set(nodeLayer, [placed]);
    }
    widestRow = Math.max(widestRow, row + 1);
  }

  const nodes = [...buckets.values()]
    .flat()
    .sort((left, right) => left.layer - right.layer || left.row - right.row);
  const edges: LayoutEdge[] = graph.edges.map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    count: edge.count,
    inferred: edge.certainties.includes("INFERRED")
  }));

  return {
    nodes,
    edges,
    width: (maxLayer + 2) * COLUMN_WIDTH,
    height: widestRow * ROW_HEIGHT
  };
}
