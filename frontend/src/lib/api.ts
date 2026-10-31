import type {
  AnalysisRelation,
  AuditEvent,
  FileDetail,
  Finding,
  FrameworkDetail,
  InventoryTree,
  MigrationGraph,
  MigrationItem,
  MigrationItemDetail,
  MigrationPlan,
  MigrationPlanSummary,
  ModuleInfo,
  Overview,
  PageResponse,
  PathOverride,
  PathOverrideInput,
  Project,
  ProjectFile,
  ProjectInput,
  RelationOverview,
  RelationGraph,
  ReportDescriptor,
  RuntimeInfo,
  Scan,
  Technology,
  TransformationBatch,
  TransformationProposal,
  TransformationRule,
  TransformationRun
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api/v1";

export class ApiRequestError extends Error {
  code: string;
  messageKey: string;
  args: unknown[];
  requestId?: string;
  status: number;

  constructor(status: number, payload: Partial<ApiErrorPayload>) {
    super(payload.message || payload.messageKey || "Request failed");
    this.name = "ApiRequestError";
    this.status = status;
    this.code = payload.code || "REQUEST_FAILED";
    this.messageKey = payload.messageKey || "error.internal";
    this.args = payload.args || [];
    this.requestId = payload.requestId;
  }
}

interface ApiErrorPayload {
  code: string;
  messageKey: string;
  message: string;
  args: unknown[];
  requestId?: string;
}

interface RequestOptions extends RequestInit {
  query?: Record<string, string | number | boolean | undefined | null>;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  if (options.query) {
    for (const [key, value] of Object.entries(options.query)) {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    }
  }
  const language = localStorage.getItem("stravique.language") || "en-US";
  const response = await fetch(url.toString(), {
    ...options,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "Accept-Language": language,
      ...(options.headers || {})
    }
  });
  if (!response.ok) {
    let payload: Partial<ApiErrorPayload> = {};
    try {
      payload = (await response.json()) as Partial<ApiErrorPayload>;
    } catch {
      payload = {};
    }
    throw new ApiRequestError(response.status, payload);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function queryString(values: Record<string, string | number | undefined | null>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value));
    }
  }
  const text = params.toString();
  return text ? `?${text}` : "";
}

export const api = {
  runtime: () => request<RuntimeInfo>("/runtime"),
  projects: () => request<Project[]>("/projects"),
  project: (projectId: string) => request<Project>(`/projects/${projectId}`),
  createProject: (input: ProjectInput) =>
    request<Project>("/projects", {
      method: "POST",
      body: JSON.stringify(input)
    }),
  updateProject: (projectId: string, input: ProjectInput) =>
    request<Project>(`/projects/${projectId}`, {
      method: "PUT",
      body: JSON.stringify(input)
    }),
  overview: (projectId: string) => request<Overview>(`/projects/${projectId}/overview`),
  scans: (projectId: string) => request<Scan[]>(`/projects/${projectId}/scans`),
  scan: (scanId: string) => request<Scan>(`/scans/${scanId}`),
  startScan: (projectId: string) =>
    request<Scan>(`/projects/${projectId}/scans`, { method: "POST" }),
  cancelScan: (scanId: string) =>
    request<Scan>(`/scans/${scanId}/cancel`, { method: "POST" }),
  files: (
    projectId: string,
    filters: {
      scanId?: string;
      q?: string;
      fileType?: string;
      scanStatus?: string;
      structureRole?: string;
      moduleId?: string;
      pathPrefix?: string;
      page?: number;
      size?: number;
    }
  ) =>
    request<PageResponse<ProjectFile>>(`/projects/${projectId}/files`, {
      query: filters
    }),
  file: (fileId: string) => request<FileDetail>(`/files/${fileId}`),
  tree: (projectId: string, scanId?: string) =>
    request<InventoryTree>(`/projects/${projectId}/tree`, {
      query: { scanId }
    }),
  modules: (projectId: string, scanId?: string) =>
    request<ModuleInfo[]>(`/projects/${projectId}/modules`, {
      query: { scanId }
    }),
  frameworks: (projectId: string, scanId?: string) =>
    request<Technology[]>(`/projects/${projectId}/frameworks`, {
      query: { scanId }
    }),
  framework: (projectId: string, technologyKey: string, scanId?: string) =>
    request<FrameworkDetail>(`/projects/${projectId}/frameworks/${technologyKey}`, {
      query: { scanId }
    }),
  reviews: (projectId: string, scanId?: string, page = 0, size = 80) =>
    request<PageResponse<Finding>>(`/projects/${projectId}/reviews`, {
      query: { scanId, page, size }
    }),
  reports: (projectId: string, scanId?: string) =>
    request<ReportDescriptor[]>(`/projects/${projectId}/reports`, {
      query: { scanId }
    }),
  reportUrl: (projectId: string, type: string, format: string, scanId?: string, lang?: string) =>
    `${API_BASE}/projects/${projectId}/reports/${type}.${format}${queryString({ scanId, lang })}`,
  pathOverrides: (projectId: string) =>
    request<PathOverride[]>(`/projects/${projectId}/path-overrides`),
  replacePathOverrides: (projectId: string, inputs: PathOverrideInput[]) =>
    request<PathOverride[]>(`/projects/${projectId}/path-overrides`, {
      method: "PUT",
      body: JSON.stringify(inputs)
    }),
  auditEvents: (projectId: string, page = 0, size = 12) =>
    request<PageResponse<AuditEvent>>(`/projects/${projectId}/audit-events`, {
      query: { page, size }
    }),
  relations: (
    projectId: string,
    filters: {
      scanId?: string;
      relationType?: string;
      certainty?: string;
      q?: string;
      page?: number;
      size?: number;
    }
  ) =>
    request<PageResponse<AnalysisRelation>>(`/projects/${projectId}/relations`, {
      query: filters
    }),
  relationOverview: (projectId: string, scanId?: string) =>
    request<RelationOverview>(`/projects/${projectId}/relations/overview`, {
      query: { scanId }
    }),
  relationGraph: (
    projectId: string,
    filters: {
      scanId?: string;
      relationType?: string;
      certainty?: string;
      q?: string;
    }
  ) =>
    request<RelationGraph>(`/projects/${projectId}/relations/graph`, {
      query: filters
    }),
  migrationPlans: (projectId: string) =>
    request<MigrationPlanSummary[]>(`/projects/${projectId}/migration-plans`),
  migrationPlan: (projectId: string, planId: string) =>
    request<MigrationPlan>(`/projects/${projectId}/migration-plans/${planId}`),
  generateMigrationPlan: (
    projectId: string,
    input: { targetProfile?: string; scanId?: string }
  ) =>
    request<MigrationPlan>(`/projects/${projectId}/migration-plans`, {
      method: "POST",
      body: JSON.stringify(input)
    }),
  migrationItems: (
    projectId: string,
    planId: string,
    filters: {
      status?: string;
      itemType?: string;
      phase?: number;
      attention?: boolean;
      included?: boolean;
      q?: string;
      page?: number;
      size?: number;
    }
  ) =>
    request<PageResponse<MigrationItem>>(
      `/projects/${projectId}/migration-plans/${planId}/items`,
      { query: filters }
    ),
  migrationItem: (projectId: string, planId: string, itemId: string) =>
    request<MigrationItemDetail>(
      `/projects/${projectId}/migration-plans/${planId}/items/${itemId}`
    ),
  updateMigrationScope: (
    projectId: string,
    planId: string,
    itemId: string,
    included: boolean
  ) =>
    request<MigrationItem>(
      `/projects/${projectId}/migration-plans/${planId}/items/${itemId}`,
      { method: "PATCH", body: JSON.stringify({ included }) }
    ),
  transformationRules: () =>
    request<TransformationRule[]>("/transformation-rules"),
  migrationGraph: (projectId: string, planId: string) =>
    request<MigrationGraph>(
      `/projects/${projectId}/migration-plans/${planId}/graph`
    ),
  transformationProposals: (projectId: string, planId: string, itemId: string) =>
    request<TransformationProposal[]>(
      `/projects/${projectId}/migration-plans/${planId}/items/${itemId}/transformation-proposals`
    ),
  proposeTransformation: (projectId: string, planId: string, itemId: string) =>
    request<TransformationProposal>(
      `/projects/${projectId}/migration-plans/${planId}/items/${itemId}/transformation-proposals`,
      { method: "POST" }
    ),
  transformationRuns: (projectId: string, planId: string, itemId: string) =>
    request<TransformationRun[]>(
      `/projects/${projectId}/migration-plans/${planId}/items/${itemId}/transformation-runs`
    ),
  applyTransformation: (projectId: string, proposalId: string, confirmed: boolean) =>
    request<TransformationRun>(
      `/projects/${projectId}/transformation-proposals/${proposalId}/apply`,
      { method: "POST", body: JSON.stringify({ confirmed }) }
    ),
  restoreTransformation: (projectId: string, runId: string, confirmed: boolean) =>
    request<TransformationRun>(
      `/projects/${projectId}/transformation-runs/${runId}/restore`,
      { method: "POST", body: JSON.stringify({ confirmed }) }
    ),
  previewTransformationBatch: (projectId: string, planId: string, itemIds: string[]) =>
    request<TransformationBatch>(
      `/projects/${projectId}/migration-plans/${planId}/transformation-batches/preview`,
      { method: "POST", body: JSON.stringify({ itemIds }) }
    ),
  applyTransformationBatch: (projectId: string, planId: string, itemIds: string[]) =>
    request<TransformationBatch>(
      `/projects/${projectId}/migration-plans/${planId}/transformation-batches/apply`,
      { method: "POST", body: JSON.stringify({ itemIds, confirmed: true }) }
    ),
  restoreTransformationBatch: (projectId: string, planId: string, runIds: string[]) =>
    request<TransformationBatch>(
      `/projects/${projectId}/migration-plans/${planId}/transformation-batches/restore`,
      { method: "POST", body: JSON.stringify({ runIds, confirmed: true }) }
    )
};
