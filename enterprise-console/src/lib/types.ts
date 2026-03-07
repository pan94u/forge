export interface Organization {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  status: string;
  createdAt: string;
  monthlyMessageQuota: number | null;
  monthlyExecQuota: number | null;
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

export interface OrgInvitation {
  id: number;
  token: string;
  orgId: string;
  role: string;
  createdBy: string;
  expiresAt: string;
  usedBy: string | null;
  usedAt: string | null;
}

export interface OrgInvitationInfo {
  token: string;
  orgId: string;
  orgName: string;
  role: string;
  expiresAt: string;
}

export interface OrgUsageSummary {
  orgId: string;
  days: number;
  totalMessages: number;
  totalExecutions: number;
  activeWorkspaces: number;
  monthlyMessageQuota: number | null;
  monthlyExecQuota: number | null;
  messagesByDay: Record<string, number>;
  executionsByDay: Record<string, number>;
}

export interface AuditLogEntry {
  id: number;
  orgId: string | null;
  actorId: string;
  action: string;
  targetType: string | null;
  targetId: string | null;
  detail: string | null;
  createdAt: string;
}

export interface UpdateQuotaRequest {
  monthlyMessageQuota: number | null;
  monthlyExecQuota: number | null;
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

export interface BudgetSummary {
  orgId: string;
  days: number;
  totalSessions: number;
  totalExecutions: number;
  roiScore: number;
  dailyActivity: Array<{ date: string; sessions: number; executions: number }>;
}

export interface TeamActivity {
  orgId: string;
  days: number;
  totalMembers: number;
  topSkills: Array<{ skillName: string; usageCount: number }>;
}

export interface SecurityPosture {
  orgId: string;
  days: number;
  totalEvents: number;
  anomalyCount: number;
  riskLevel: 'low' | 'medium' | 'high';
  actionBreakdown: Array<{ action: string; count: number }>;
  recentAnomalies: Array<{ timestamp: string; actorId: string; action: string; detail: string | null }>;
}

export interface KnowledgeHealth {
  orgId: string;
  totalTags: number;
  activeTags: number;
  draftTags: number;
  emptyTags: number;
  coverageScore: number;
  overallHealth: number;
  tagsByStatus: Array<{ status: string; count: number }>;
}

export interface ProcessFlow {
  id: string;
  orgId: string | null;
  workspaceId: string | null;
  flowName: string;
  flowType: string | null;
  rawMermaid: string;
  parsedNodes: string | null;
  parsedEdges: string | null;
  nodeCount: number | null;
  edgeCount: number | null;
  extractedAt: string;
  sourceTagId: string | null;
}
