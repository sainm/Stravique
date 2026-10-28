import type { MigrationGraph, RelationGraph } from "../../lib/types";

export function toRelationGraph(graph?: MigrationGraph): RelationGraph | undefined {
  if (!graph || graph.nodes.length === 0) {
    return undefined;
  }
  return {
    projectId: "",
    relationCount: graph.edgeCount,
    limit: graph.itemLimit,
    nodes: graph.nodes.map((node) => ({
      id: node.id,
      path: node.filePath || node.title,
      fileName: node.title,
      fileType: node.itemType,
      incoming: 0,
      outgoing: 0
    })),
    edges: graph.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      relationTypes: edge.relationTypes,
      certainties: edge.certainties,
      count: edge.count
    })),
    truncated: graph.truncated
  };
}
