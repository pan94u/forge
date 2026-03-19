import type {
  Organization,
  OrgMember,
  OrgModelConfig,
  OrgDbConnection,
  OrgEnvConfig,
  Workspace,
  OrgInvitation,
  OrgInvitationInfo,
  OrgUsageSummary,
  AuditLogEntry,
  UpdateQuotaRequest,
  CreateOrgRequest,
  UpdateOrgRequest,
  AddMemberRequest,
  UpsertModelConfigRequest,
  CreateDbConnectionRequest,
  UpsertEnvConfigRequest,
  BudgetSummary,
  TeamActivity,
  SecurityPosture,
  KnowledgeHealth,
  ProcessFlow,
  ArchitectureSummary,
  ProcessSummary,
  ComplianceSummary,
  CapacitySummary,
  VendorSummary,
  EvalTask,
  EvalRun,
  EvalResult,
  CreateEvalTaskRequest,
  CreateEvalRunRequest,
} from "./types";

// Next.js basePath (e.g. "/console") — needed because browser fetch() doesn't
// auto-prepend basePath like <Link> does. Without this, requests go to /api/*
// which nginx routes directly to backend (bypassing Console's auth proxy layer).
const BASE = process.env.NEXT_PUBLIC_BASE_PATH || "";

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    // Try to extract a human-readable message from JSON error responses
    let message = body;
    try {
      const json = JSON.parse(body);
      message = json.error ?? json.message ?? json.detail ?? body;
    } catch {
      // body is not JSON, use as-is
    }
    throw new Error(message || `HTTP ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

// --- Organizations ---

export const api = {
  orgs: {
    list: () => fetchJson<Organization[]>("/api/admin/orgs"),
    get: (id: string) => fetchJson<Organization>(`/api/admin/orgs/${id}`),
    create: (req: CreateOrgRequest) =>
      fetchJson<Organization>("/api/admin/orgs", {
        method: "POST",
        body: JSON.stringify(req),
      }),
    update: (id: string, req: UpdateOrgRequest) =>
      fetchJson<Organization>(`/api/admin/orgs/${id}`, {
        method: "PUT",
        body: JSON.stringify(req),
      }),
    delete: (id: string) =>
      fetchJson<void>(`/api/admin/orgs/${id}`, { method: "DELETE" }),
  },

  members: {
    list: (orgId: string) =>
      fetchJson<OrgMember[]>(`/api/admin/orgs/${orgId}/members`),
    add: (orgId: string, req: AddMemberRequest) =>
      fetchJson<OrgMember>(`/api/admin/orgs/${orgId}/members`, {
        method: "POST",
        body: JSON.stringify(req),
      }),
    remove: (orgId: string, userId: string) =>
      fetchJson<void>(`/api/admin/orgs/${orgId}/members/${userId}`, {
        method: "DELETE",
      }),
  },

  workspaces: {
    listByOrg: (orgId: string) =>
      fetchJson<Workspace[]>(`/api/admin/orgs/${orgId}/workspaces`),
    bind: (orgId: string, wsId: string) =>
      fetchJson<{ success: boolean }>(
        `/api/admin/orgs/${orgId}/workspaces/${wsId}/bind`,
        { method: "POST" }
      ),
    unbind: (orgId: string, wsId: string) =>
      fetchJson<void>(`/api/admin/orgs/${orgId}/workspaces/${wsId}/bind`, {
        method: "DELETE",
      }),
    listAll: () => fetchJson<Workspace[]>("/api/workspaces"),
  },

  modelConfigs: {
    list: (orgId: string) =>
      fetchJson<OrgModelConfig[]>(`/api/admin/orgs/${orgId}/model-configs`),
    upsert: (orgId: string, provider: string, req: UpsertModelConfigRequest) =>
      fetchJson<OrgModelConfig>(
        `/api/admin/orgs/${orgId}/model-configs/${provider}`,
        { method: "PUT", body: JSON.stringify(req) }
      ),
  },

  dbConnections: {
    list: (orgId: string) =>
      fetchJson<OrgDbConnection[]>(`/api/admin/orgs/${orgId}/db-connections`),
    create: (orgId: string, req: CreateDbConnectionRequest) =>
      fetchJson<OrgDbConnection>(`/api/admin/orgs/${orgId}/db-connections`, {
        method: "POST",
        body: JSON.stringify(req),
      }),
    delete: (orgId: string, id: string) =>
      fetchJson<void>(`/api/admin/orgs/${orgId}/db-connections/${id}`, {
        method: "DELETE",
      }),
    test: (orgId: string, id: string) =>
      fetchJson<{ success: boolean; message: string }>(
        `/api/admin/orgs/${orgId}/db-connections/${id}/test`,
        { method: "POST" }
      ),
  },

  invitations: {
    create: (orgId: string, role: string) =>
      fetchJson<OrgInvitation>(`/api/admin/orgs/${orgId}/invitations`, {
        method: "POST",
        body: JSON.stringify({ role }),
      }),
    get: (token: string) =>
      fetchJson<OrgInvitationInfo>(`/api/admin/invitations/${token}`),
    accept: (token: string) =>
      fetchJson<OrgMember>(`/api/admin/invitations/${token}/accept`, {
        method: "POST",
      }),
  },

  usage: {
    get: (orgId: string, days = 7) =>
      fetchJson<OrgUsageSummary>(`/api/admin/orgs/${orgId}/usage?days=${days}`),
  },

  auditLogs: {
    listByOrg: (orgId: string, page = 0) =>
      fetchJson<{ content: AuditLogEntry[]; totalPages: number; totalElements: number }>(
        `/api/admin/orgs/${orgId}/audit-logs?page=${page}&size=50`
      ),
    listAll: (page = 0) =>
      fetchJson<{ content: AuditLogEntry[]; totalPages: number; totalElements: number }>(
        `/api/admin/audit-logs?page=${page}&size=100`
      ),
  },

  quota: {
    update: (orgId: string, req: UpdateQuotaRequest) =>
      fetchJson<Organization>(`/api/admin/orgs/${orgId}/quota`, {
        method: "PUT",
        body: JSON.stringify(req),
      }),
  },

  envConfigs: {
    list: (orgId: string, category?: string) => {
      const url = category
        ? `/api/admin/orgs/${orgId}/env-configs?category=${encodeURIComponent(category)}`
        : `/api/admin/orgs/${orgId}/env-configs`;
      return fetchJson<OrgEnvConfig[]>(url);
    },
    upsert: (
      orgId: string,
      category: string,
      key: string,
      req: UpsertEnvConfigRequest
    ) =>
      fetchJson<OrgEnvConfig>(
        `/api/admin/orgs/${orgId}/env-configs/${category}/${key}`,
        { method: "PUT", body: JSON.stringify(req) }
      ),
    delete: (orgId: string, category: string, key: string) =>
      fetchJson<void>(
        `/api/admin/orgs/${orgId}/env-configs/${category}/${key}`,
        { method: "DELETE" }
      ),
  },

  governance: {
    getBudget: (orgId: string, days = 30) =>
      fetchJson<BudgetSummary>(`/api/governance/${orgId}/budget?days=${days}`),
    getTeam: (orgId: string, days = 30) =>
      fetchJson<TeamActivity>(`/api/governance/${orgId}/team?days=${days}`),
    getSecurity: (orgId: string, days = 30) =>
      fetchJson<SecurityPosture>(`/api/governance/${orgId}/security?days=${days}`),
    getKnowledge: (orgId: string) =>
      fetchJson<KnowledgeHealth>(`/api/governance/${orgId}/data`),
    getProcessFlows: (orgId: string) =>
      fetchJson<ProcessFlow[]>(`/api/governance/${orgId}/process`),
    createSnapshot: (orgId: string) =>
      fetchJson<void>(`/api/governance/${orgId}/snapshot`, { method: 'POST' }),
    getArchitecture: (orgId: string) =>
      fetchJson<ArchitectureSummary>(`/api/governance/${orgId}/architecture`),
    getProcessSummary: (orgId: string) =>
      fetchJson<ProcessSummary>(`/api/governance/${orgId}/process-summary`),
    getCompliance: (orgId: string, days = 30) =>
      fetchJson<ComplianceSummary>(`/api/governance/${orgId}/compliance?days=${days}`),
    getCapacity: (orgId: string) =>
      fetchJson<CapacitySummary>(`/api/governance/${orgId}/capacity`),
    getVendor: (orgId: string, days = 30) =>
      fetchJson<VendorSummary>(`/api/governance/${orgId}/vendor?days=${days}`),
  },

  eval: {
    listTasks: (orgId?: string, type?: string, difficulty?: string) => {
      const params = new URLSearchParams();
      if (orgId) params.set("orgId", orgId);
      if (type) params.set("type", type);
      if (difficulty) params.set("difficulty", difficulty);
      const qs = params.toString();
      return fetchJson<EvalTask[]>(`/api/eval/tasks${qs ? `?${qs}` : ""}`);
    },
    getTask: (id: string) => fetchJson<EvalTask>(`/api/eval/tasks/${id}`),
    createTask: (req: CreateEvalTaskRequest) =>
      fetchJson<EvalTask>("/api/eval/tasks", {
        method: "POST",
        body: JSON.stringify(req),
      }),
    updateTask: (id: string, req: CreateEvalTaskRequest) =>
      fetchJson<EvalTask>(`/api/eval/tasks/${id}`, {
        method: "PUT",
        body: JSON.stringify(req),
      }),
    deleteTask: (id: string) =>
      fetchJson<{ deleted: boolean }>(`/api/eval/tasks/${id}`, { method: "DELETE" }),
    importYaml: (yamlContents: string[], orgId?: string) =>
      fetchJson<{ imported: number; skipped: number; errors: string[] }>(
        "/api/eval/tasks/import-yaml",
        { method: "POST", body: JSON.stringify({ yamlContents, orgId }) }
      ),
    listRuns: (orgId?: string) => {
      const qs = orgId ? `?orgId=${orgId}` : "";
      return fetchJson<EvalRun[]>(`/api/eval/runs${qs}`);
    },
    getRun: (id: string) => fetchJson<EvalRun>(`/api/eval/runs/${id}`),
    getRunResults: (runId: string) =>
      fetchJson<EvalResult[]>(`/api/eval/runs/${runId}/results`),
    createRun: (req: CreateEvalRunRequest) =>
      fetchJson<EvalRun>("/api/eval/runs", {
        method: "POST",
        body: JSON.stringify(req),
      }),
    cancelRun: (id: string) =>
      fetchJson<EvalRun>(`/api/eval/runs/${id}/cancel`, { method: "POST" }),
  },
};
