import { describe, expect, it } from "vitest";
import {
  COLUMN_WIDTH,
  ROW_HEIGHT,
  layoutRelationGraph
} from "../features/relations/relationLayout";
import type {
  RelationGraph,
  RelationGraphEdge,
  RelationGraphNode
} from "../lib/types";

function node(id: string): RelationGraphNode {
  return {
    id,
    path: `src/${id}.java`,
    fileName: `${id}.java`,
    fileType: "JAVA",
    incoming: 0,
    outgoing: 0
  };
}

function edge(
  source: string,
  target: string,
  certainties: string[] = ["ANALYZED"],
  count = 1
): RelationGraphEdge {
  return {
    id: `${source}->${target}`,
    source,
    target,
    relationTypes: ["JAVA_IMPORT"],
    certainties,
    count
  };
}

function graph(
  nodes: RelationGraphNode[],
  edges: RelationGraphEdge[]
): RelationGraph {
  return { projectId: "project", relationCount: edges.length, nodes, edges };
}

describe("layoutRelationGraph", () => {
  it("returns an empty layout without nodes", () => {
    expect(layoutRelationGraph(undefined).nodes).toEqual([]);

    const empty = layoutRelationGraph(graph([], []));
    expect(empty.nodes).toEqual([]);
    expect(empty.edges).toEqual([]);
    expect(empty.width).toBe(0);
    expect(empty.height).toBe(0);
  });

  it("places a chain in successive columns", () => {
    const layout = layoutRelationGraph(
      graph([node("a"), node("b"), node("c")], [edge("a", "b"), edge("b", "c")])
    );
    const byId = new Map(layout.nodes.map((item) => [item.id, item]));

    expect(byId.get("a")?.layer).toBe(0);
    expect(byId.get("b")?.layer).toBe(1);
    expect(byId.get("c")?.layer).toBe(2);
    expect(byId.get("c")?.x).toBe(2 * COLUMN_WIDTH);
    expect(byId.get("a")?.y).toBe(0);
  });

  it("stacks siblings inside one column", () => {
    const layout = layoutRelationGraph(
      graph(
        [node("root"), node("left"), node("right")],
        [edge("root", "left"), edge("root", "right")]
      )
    );
    const byId = new Map(layout.nodes.map((item) => [item.id, item]));

    expect(byId.get("left")?.layer).toBe(1);
    expect(byId.get("right")?.layer).toBe(1);
    expect(byId.get("left")?.x).toBe(byId.get("right")?.x);
    expect(byId.get("left")?.y).toBe(0);
    expect(byId.get("right")?.y).toBe(ROW_HEIGHT);
    expect(layout.height).toBe(2 * ROW_HEIGHT);
  });

  it("keeps cyclic nodes in a terminal column instead of looping", () => {
    const layout = layoutRelationGraph(
      graph([node("a"), node("b")], [edge("a", "b"), edge("b", "a")])
    );

    expect(layout.nodes).toHaveLength(2);
    const layers = layout.nodes.map((item) => item.layer);
    expect(new Set(layers).size).toBe(1);
    expect(layers[0]).toBeGreaterThan(0);
  });

  it("gives isolated nodes a place", () => {
    const layout = layoutRelationGraph(graph([node("lonely")], []));

    expect(layout.nodes).toHaveLength(1);
    expect(layout.nodes[0].layer).toBe(0);
    expect(layout.nodes[0].x).toBe(0);
  });

  it("marks both endpoints of an inferred relation", () => {
    const layout = layoutRelationGraph(
      graph(
        [node("a"), node("b"), node("c")],
        [edge("a", "b", ["INFERRED"]), edge("b", "c")]
      )
    );
    const byId = new Map(layout.nodes.map((item) => [item.id, item]));

    expect(byId.get("a")?.inferred).toBe(true);
    expect(byId.get("b")?.inferred).toBe(true);
    expect(byId.get("c")?.inferred).toBe(false);
  });

  it("preserves edge multiplicity and certainty", () => {
    const layout = layoutRelationGraph(
      graph([node("a"), node("b")], [edge("a", "b", ["INFERRED"], 3)])
    );

    expect(layout.edges).toHaveLength(1);
    expect(layout.edges[0].count).toBe(3);
    expect(layout.edges[0].inferred).toBe(true);
  });

  it("is deterministic for the same input", () => {
    const input = graph(
      [node("a"), node("b"), node("c"), node("d")],
      [edge("a", "b"), edge("a", "c"), edge("b", "d")]
    );

    expect(layoutRelationGraph(input)).toEqual(layoutRelationGraph(input));
  });

  it("sorts nodes by column then row", () => {
    const layout = layoutRelationGraph(
      graph(
        [node("late"), node("root"), node("first")],
        [edge("root", "first"), edge("first", "late")]
      )
    );

    expect(layout.nodes.map((item) => item.id)).toEqual(["root", "first", "late"]);
  });
});
