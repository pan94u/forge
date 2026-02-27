export interface Organization {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: string;
  createdAt: string;
}

export interface OrgMember {
  orgId: string;
  userId: string;
  role: string;
  joinedAt: string;
}

export interface OrgModelConfig {
  id: string;
  orgId: string;
  provider: string;
  enabled: boolean;
  apiKeyMasked: string | null;
  baseUrl: string | null;
  modelAllowlist: string[] | null;
  updatedAt: string;
}

export interface OrgDbConnection {
  id: string;
  orgId: string;
  name: string;
  jdbcUrl: string;
  username: string | null;
  accessLevel: string;
  createdAt: string;
}

export interface OrgEnvConfig {
  id: string;
  orgId: string;
  category: string;
  configKey: string;
  configValue: string | null;
  isSensitive: boolean;
  description: string | null;
}

export interface Workspace {
  id: string;
  name: string;
  description: string;
  status: string;
  owner: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrgRequest {
  name: string;
  slug: string;
  description?: string;
}

export interface UpdateOrgRequest {
  name?: string;
  description?: string;
  status?: string;
}

export interface AddMemberRequest {
  userId: string;
  role: string;
}

export interface UpsertModelConfigRequest {
  enabled: boolean;
  apiKey?: string;
  baseUrl?: string;
  modelAllowlist?: string[];
}

export interface CreateDbConnectionRequest {
  name: string;
  jdbcUrl: string;
  username?: string;
  password?: string;
  accessLevel: string;
}

export interface UpsertEnvConfigRequest {
  configKey: string;
  configValue: string | null;
  isSensitive: boolean;
  description?: string;
}
