import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ScanComparisonModal from "../features/overview/ScanComparisonModal";

const state = vi.hoisted(() => {
  const scan = (id: string, sequence: number) => ({
    id,
    sequence,
    status: "COMPLETED",
    createdAt: "2026-01-01T00:00:00Z",
    finishedAt: "2026-01-01T00:01:00Z",
    fileCount: 10,
    moduleCount: 1,
    analyzedCount: 10,
    parseFailureCount: 0,
    skippedCount: 0,
    findingCount: 0,
    reviewCount: 0,
    technologyCount: 0,
    removedCount: 0,
    analyzerVersion: "1.0.0"
  });
  const file = (fileType: string, sizeBytes: number, sha256?: string) => ({
    fileType,
    sizeBytes,
    sha256,
    encoding: "UTF-8",
    scanStatus: "ANALYZED",
    parseStatus: "PARSED",
    generated: false
  });
  return {
    scans: vi.fn(),
    comparison: vi.fn(),
    first: scan("scan-1", 1),
    second: scan("scan-2", 2),
    comparisonData: {
      projectId: "project-1",
      scan: scan("scan-2", 2),
      baseScan: scan("scan-1", 1),
      summary: {
        added: 1,
        removed: 1,
        changed: 1,
        unchanged: 3,
        unknown: 1
      },
      rows: [
        {
          status: "ADDED",
          relativePath: "src/Added.java",
          after: file("JAVA", 120, "aaaaaaaaaaaa1111")
        },
        {
          status: "REMOVED",
          relativePath: "src/Removed.java",
          before: file("JAVA", 80, "bbbbbbbbbbbb2222")
        },
        {
          status: "CHANGED",
          relativePath: "src/Changed.java",
          before: file("JAVA", 40, "cccccccccccc3333"),
          after: file("JAVA", 44, "dddddddddddd4444")
        },
        {
          status: "UNKNOWN",
          relativePath: "src/Unknown.bin",
          before: file("BINARY", 16),
          after: file("BINARY", 16)
        }
      ],
      truncated: false,
      rowLimit: 2000
    }
  };
});

vi.mock("../lib/api", () => ({
  api: {
    scans: state.scans,
    scanComparison: state.comparison,
    scanComparisonExportUrl: (
      projectId: string,
      scanId: string,
      baseScanId: string,
      format: string,
      lang?: string
    ) =>
      `/api/v1/projects/${projectId}/scans/${scanId}/comparison/export?baseScanId=${baseScanId}&format=${format}${
        lang ? `&lang=${lang}` : ""
      }`
  }
}));

function renderModal() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  return render(
    <QueryClientProvider client={client}>
      <ScanComparisonModal
        projectId="project-1"
        open
        onClose={() => undefined}
      />
    </QueryClientProvider>
  );
}

describe("ScanComparisonModal", () => {
  beforeEach(() => {
    state.scans.mockReset();
    state.scans.mockResolvedValue([state.second, state.first]);
    state.comparison.mockReset();
    state.comparison.mockResolvedValue(state.comparisonData);
  });

  it("defaults to the latest completed scan and the previous base", async () => {
    renderModal();

    await waitFor(() =>
      expect(state.comparison).toHaveBeenCalledWith(
        "project-1",
        "scan-2",
        "scan-1"
      )
    );
    expect(
      (screen.getByLabelText("Current scan") as HTMLSelectElement).value
    ).toBe("scan-2");
    expect(
      (screen.getByLabelText("Base scan") as HTMLSelectElement).value
    ).toBe("scan-1");
  });

  it("renders every summary metric and groups changed rows", async () => {
    renderModal();

    expect(await screen.findByText("src/Added.java")).toBeInTheDocument();
    expect(screen.getByText("src/Removed.java")).toBeInTheDocument();
    expect(screen.getByText("src/Changed.java")).toBeInTheDocument();
    expect(screen.getByText("src/Unknown.bin")).toBeInTheDocument();
    expect(screen.getByText("Added", { selector: ".migration-diff-status" }))
      .toBeInTheDocument();
    expect(screen.getByText("Removed", { selector: ".migration-diff-status" }))
      .toBeInTheDocument();
    expect(screen.getByText("Changed", { selector: ".migration-diff-status" }))
      .toBeInTheDocument();
    expect(screen.getByText("Unknown", { selector: ".migration-diff-status" }))
      .toBeInTheDocument();
    const metrics = within(
      document.querySelector(".scan-comparison-metrics") as HTMLElement
    );
    expect(metrics.getByText("Added").parentElement).toHaveTextContent("Added1");
    expect(metrics.getByText("Removed").parentElement).toHaveTextContent(
      "Removed1"
    );
    expect(metrics.getByText("Changed").parentElement).toHaveTextContent(
      "Changed1"
    );
    expect(metrics.getByText("Unchanged").parentElement).toHaveTextContent(
      "Unchanged3"
    );
    expect(metrics.getByText("Unknown").parentElement).toHaveTextContent(
      "Unknown1"
    );
  });

  it("refetches when the current scan changes", async () => {
    const user = userEvent.setup();
    renderModal();

    await waitFor(() =>
      expect(state.comparison).toHaveBeenCalledWith(
        "project-1",
        "scan-2",
        "scan-1"
      )
    );
    await user.selectOptions(screen.getByLabelText("Current scan"), "scan-1");
    await waitFor(() =>
      expect(state.comparison).toHaveBeenCalledWith(
        "project-1",
        "scan-1",
        "scan-2"
      )
    );
  });

  it("states when fewer than two completed scans exist", async () => {
    state.scans.mockResolvedValue([state.second]);
    renderModal();

    expect(
      await screen.findByText(
        "At least two completed scans are required to compare inventory snapshots."
      )
    ).toBeInTheDocument();
    expect(state.comparison).not.toHaveBeenCalled();
  });
});
