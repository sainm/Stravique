import { describe, expect, it } from "vitest";
import { toRelationGraph } from "../features/migration/migrationGraph";
import { layoutRelationGraph } from "../features/relations/relationLayout";
import type { MigrationGraph } from "../lib/types";

const graph: MigrationGraph = {
  planId: "plan",
  itemCount: 2,
  edgeCount: 1,
  itemLimit: 800,
  edgeLimit: 3000,
  truncated: false,
  nodes: [
    {
      id: "prerequisite",
      title: "OrderDao.java",
      filePath: "domain/OrderDao.java",
      itemType: "DATA_ACCESS",
      targetKey: "SPRING_JDBC",
      status: "REVIEW_REQUIRED",
      riskLevel: "HIGH",
      phase: 1,
      priority: 1
    },
    {
      id: "dependent",
      title: "OrderService.java",
      filePath: "web-app/OrderService.java",
      itemType: "COMPONENT_BEAN",
      targetKey: "SPRING_DI",
      status: "READY",
      riskLevel: "MEDIUM",
      phase: 2,
      priority: 3
    }
  ],
  edges: [
    {
      id: "prerequisite->dependent",
      source: "prerequisite",
      target: "dependent",
      relationTypes: ["JAVA_IMPORT"],
      certainties: ["ANALYZED"],
      count: 1
    }
  ]
};

describe("migration graph adapter", () => {
  it("returns no relation graph for an empty plan graph", () => {
    expect(toRelationGraph(undefined)).toBeUndefined();
    expect(toRelationGraph({ ...graph, nodes: [], edges: [] })).toBeUndefined();
  });

  it("maps persisted migration nodes and prerequisite edges", () => {
    const relationGraph = toRelationGraph(graph);
    expect(relationGraph?.nodes).toHaveLength(2);
    expect(relationGraph?.nodes[0]).toMatchObject({
      id: "prerequisite",
      fileName: "OrderDao.java",
      path: "domain/OrderDao.java",
      fileType: "DATA_ACCESS"
    });
    expect(relationGraph?.edges[0]).toMatchObject({
      source: "prerequisite",
      target: "dependent",
      relationTypes: ["JAVA_IMPORT"]
    });
    expect(relationGraph?.truncated).toBe(false);
  });

  it("lays prerequisites before dependent items", () => {
    const layout = layoutRelationGraph(toRelationGraph(graph));
    const prerequisite = layout.nodes.find((node) => node.id === "prerequisite");
    const dependent = layout.nodes.find((node) => node.id === "dependent");
    expect(prerequisite?.layer).toBe(0);
    expect(dependent?.layer).toBe(1);
  });
});
