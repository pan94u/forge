import type {
  Organization,
  OrgMember,
  OrgModelConfig,
  OrgDbConnection,
  OrgEnvConfig,
  Workspace,
  CreateOrgRequest,
  UpdateOrgRequest,
  AddMemberRequest,
  UpsertModelConfigRequest,
  CreateDbConnectionRequest,
  UpsertEnvConfigRequest,
} from "./types";

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`API error ${res.status}: ${body}`);
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
};
