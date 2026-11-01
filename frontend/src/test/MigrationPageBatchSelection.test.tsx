import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MigrationPage from "../features/migration/MigrationPage";

const state = vi.hoisted(() => {
  const plan = {
    id: "plan-1",
    projectId: "project-1",
    scanId: "scan-1",
    sequence: 1,
    sourceProfile: "SEASAR2_STRUTS1",
    targetProfile: "SPRING_BOOT_MVC",
    sourceScanSequence: 1,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    itemCount: 2,
    includedCount: 2,
    readyCount: 2,
    reviewCount: 0,
    blockedCount: 0,
    dependencyCount: 0,
    phaseCount: 1,
    analyzerVersion: "1.0.0",
    stale: false
  };
  const items = ["item-1", "item-2"].map((id) => ({
    id,
    planId: "plan-1",
    itemKey: id,
    itemType: "COMPONENT_BEAN",
    targetKey: "SPRING_DI",
    title: `${id}.java`,
    primaryFilePath: `src/${id}.java`,
    phase: 1,
    priority: 1,
    automationLevel: "ASSISTED",
    riskLevel: "LOW",
    status: "READY",
    included: true,
    reasonCode: "COMPONENT_BEAN",
    findingCount: 1,
    dependencyCount: 0,
    dependentCount: 0
  }));
  return { plan, items, updateScope: vi.fn(), coverage: vi.fn() };
});

vi.mock("../lib/api", () => ({
  api: {
    project: async () => ({ id: "project-1", name: "Project" }),
    scans: async () => [],
    migrationPlans: async () => [state.plan],
    migrationPlanOptions: async () => [state.plan],
    migrationPlan: async () => ({ summary: state.plan }),
    migrationItems: async () => ({
      content: state.items,
      totalElements: state.items.length,
      page: 0,
      size: 20
    }),
    migrationItem: async (_projectId: string, _planId: string, itemId: string) => ({
      item: state.items.find((candidate) => candidate.id === itemId),
      dependencies: [],
      dependents: [],
      findings: []
    }),
    migrationGraph: async () => ({
      nodes: [],
      edges: [],
      itemCount: 0,
      edgeCount: 0,
      itemLimit: 800,
      edgeLimit: 3000,
      truncated: false
    }),
    transformationCoverage: state.coverage,
    transformationBackups: async () => ({
      total: 0,
      restorable: 0,
      prunable: 0,
      bytes: 0,
      items: []
    }),
    transformationPlanRuns: async () => [],
    transformationRules: async () => [],
    transformationProposals: async () => [],
    transformationRuns: async () => [],
    updateMigrationScope: state.updateScope
  }
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  const view = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/projects/project-1/migration"]}>
        <Routes>
          <Route path="/projects/:projectId/migration" element={<MigrationPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return view;
}

describe("MigrationPage batch selection", () => {
  beforeEach(() => {
    state.updateScope.mockReset();
    state.updateScope.mockResolvedValue({});
    state.coverage.mockReset();
    state.coverage.mockResolvedValue({
      items: [],
      summary: {
        items: 0,
        supported: 0,
        unsupported: 0,
        eligible: 0,
        applied: 0,
        restored: 0
      }
    });
  });

  it("drops an excluded item from the batch selection", async () => {
    const user = userEvent.setup();
    renderPage();

    const checkboxes = await screen.findAllByRole("checkbox", {
      name: /select .* for batch/i
    });
    expect(checkboxes).toHaveLength(2);
    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);
    await waitFor(() => expect(screen.getByText("2 selected")).toBeInTheDocument());

    await user.click(screen.getAllByRole("button", { name: /item-1\.java/i })[0]);
    const exclude = await screen.findByRole("button", { name: /exclude from plan/i });
    await user.click(exclude);

    await waitFor(() =>
      expect(state.updateScope).toHaveBeenCalledWith(
        "project-1",
        "plan-1",
        "item-1",
        false
      )
    );
    await waitFor(() => expect(screen.getByText("1 selected")).toBeInTheDocument());
  });

  it("refetches the coverage after a scope change", async () => {
    const user = userEvent.setup();
    renderPage();

    const checkboxes = await screen.findAllByRole("checkbox", {
      name: /select .* for batch/i
    });
    await waitFor(() => expect(state.coverage).toHaveBeenCalled());
    const before = state.coverage.mock.calls.length;

    await user.click(checkboxes[0]);
    await user.click(screen.getAllByRole("button", { name: /item-1\.java/i })[0]);
    await user.click(await screen.findByRole("button", { name: /exclude from plan/i }));

    await waitFor(() =>
      expect(state.coverage.mock.calls.length).toBeGreaterThan(before)
    );
  });
});
