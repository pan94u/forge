/**
 * Forge Eval API Client — consumes the 17 REST endpoints from eval-api module.
 */

// ── Types ────────────────────────────────────────────────────────

export type Platform = "FORGE" | "SYNAPSE" | "APPLICATION";
export type AgentType = "CODING" | "CONVERSATIONAL" | "RESEARCH" | "COMPUTER_USE";
export type Lifecycle = "CAPABILITY" | "REGRESSION" | "SATURATED";
export type Difficulty = "EASY" | "MEDIUM" | "HARD" | "EXPERT";
export type TrialOutcome = "PASS" | "FAIL" | "PARTIAL" | "ERROR";
export type GraderType = "CODE_BASED" | "MODEL_BASED" | "HUMAN";
export type RunStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type ReviewStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "SKIPPED";

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SuiteResponse {
  id: string;
  name: string;
  description: string;
  platform: Platform;
  agentType: AgentType;
  lifecycle: Lifecycle;
  tags: string[];
  taskCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AssertionConfig {
  type: string;
  expected: string;
  description: string;
  caseSensitive?: boolean;
  extras?: Record<string, unknown>;
}

export interface RubricCriterion {
  criterion: string;
  weight: number;
  description: string;
  scale?: number[];
}

export interface GraderConfig {
  type: GraderType;
  assertions?: AssertionConfig[];
  model?: string;
  rubric?: RubricCriterion[];
}

export interface EvalTask {
  id: string;
  suiteId: string;
  name: string;
  description: string;
  prompt: string;
  context: Record<string, unknown>;
  referenceAnswer?: string;
  graderConfigs: GraderConfig[];
  difficulty: Difficulty;
  tags: string[];
  baselinePassRate: number;
  saturationCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AssertionResult {
  description: string;
  passed: boolean;
  expected: string;
  actual: string;
  assertionType: string;
}

export interface GradeResponse {
  id: string;
  graderType: GraderType;
  score: number;
  passed: boolean;
  assertionResults: AssertionResult[];
  explanation?: string;
  confidence: number;
}

export interface TrialResponse {
  id: string;
  taskId: string;
  taskName: string;
  trialNumber: number;
  outcome: TrialOutcome;
  score: number;
  durationMs: number;
  grades: GradeResponse[];
}

export interface RunSummary {
  totalTasks: number;
  totalTrials: number;
  passedTrials: number;
  failedTrials: number;
  errorTrials: number;
  overallPassRate: number;
  averageScore: number;
  totalDurationMs: number;
  passAtK?: number;
  passPowerK?: number;
}

export interface RunResponse {
  id: string;
  suiteId: string;
  suiteName: string;
  status: RunStatus;
  trialsPerTask: number;
  model?: string;
  summary?: RunSummary;
  trials: TrialResponse[];
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface TrendDataPoint {
  runId: string;
  timestamp: string;
  passRate: number;
  averageScore: number;
  passAtK?: number;
  passPowerK?: number;
  totalTrials: number;
  lifecycle: string;
}

export interface TrendResponse {
  suiteId: string;
  suiteName: string;
  dataPoints: TrendDataPoint[];
}

export interface RegressionItem {
  taskId: string;
  taskName: string;
  type: string;
  baselinePassRate: number;
  currentPassRate: number;
  isStatisticallySignificant: boolean;
  message: string;
}

export interface RegressionReport {
  regressions: RegressionItem[];
  hasRegressions: boolean;
}

export interface ReviewQueueItem {
  gradeId: string;
  trialId: string;
  taskId: string;
  taskName: string;
  graderType: GraderType;
  autoScore: number;
  confidence: number;
  reviewReasons: string[];
  status: ReviewStatus;
  createdAt: string;
}

export interface ReviewResponse {
  gradeId: string;
  humanScore: number;
  humanPassed: boolean;
  reviewer: string;
  explanation: string;
  calibrationDelta: number;
  completedAt: string;
}

export interface GraderCalibration {
  graderType: GraderType;
  reviewCount: number;
  averageAutoScore: number;
  averageHumanScore: number;
  agreementRate: number;
}

export interface CalibrationMetrics {
  totalReviews: number;
  averageAutoScore: number;
  averageHumanScore: number;
  scoreDelta: number;
  agreementRate: number;
  cohensKappa: number;
  byGraderType: Record<string, GraderCalibration>;
}

export interface LifecycleEvalResponse {
  taskId: string;
  taskName: string;
  currentLifecycle: string;
  recommendedLifecycle: string;
  shouldTransition: boolean;
  reason: string;
  consecutivePassingRuns: number;
  recentPassRate: number;
  recentPassPowerK?: number;
}

export interface TranscriptTurn {
  role: string;
  content: string;
  toolCalls?: { toolName: string; arguments: Record<string, unknown>; result?: string }[];
}

// ── Request types ────────────────────────────────────────────────

export interface CreateSuiteRequest {
  name: string;
  description?: string;
  platform: Platform;
  agentType: AgentType;
  lifecycle?: Lifecycle;
  tags?: string[];
}

export interface CreateTaskRequest {
  name: string;
  description?: string;
  prompt: string;
  graderConfigs: GraderConfig[];
  difficulty?: Difficulty;
  tags?: string[];
  baselinePassRate?: number;
}

export interface CreateRunRequest {
  suiteId: string;
  trialsPerTask?: number;
  model?: string;
}

export interface SubmitTranscriptRequest {
  suiteId: string;
  taskId: string;
  source: "FORGE" | "SYNAPSE" | "EXTERNAL";
  turns: TranscriptTurn[];
  metadata?: Record<string, unknown>;
}

export interface SubmitReviewRequest {
  score: number;
  passed: boolean;
  explanation: string;
  reviewer: string;
}

// ── Auth helper (reuse pattern from workspace-api) ───────────────

function getAuthHeader(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("forge_access_token");
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

function headers(extra: Record<string, string> = {}): Record<string, string> {
  return { ...getAuthHeader(), ...extra };
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 401 && typeof window !== "undefined") {
    localStorage.removeItem("forge_access_token");
    localStorage.removeItem("forge_refresh_token");
    localStorage.removeItem("forge_token_expiry");
    window.location.href = "/login";
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Eval API error (${response.status}): ${text}`);
  }
  return response.json();
}

// ── API Client ───────────────────────────────────────────────────

const BASE = "/api/eval/v1";

export const evalApi = {
  // Suite
  listSuites(params?: { platform?: string; agentType?: string; page?: number; size?: number }) {
    const q = new URLSearchParams();
    if (params?.platform) q.set("platform", params.platform);
    if (params?.agentType) q.set("agentType", params.agentType);
    if (params?.page != null) q.set("page", String(params.page));
    if (params?.size != null) q.set("size", String(params.size));
    const qs = q.toString();
    return fetch(`${BASE}/suites${qs ? `?${qs}` : ""}`, { headers: headers() }).then(r => handleResponse<PageResponse<SuiteResponse>>(r));
  },

  createSuite(data: CreateSuiteRequest) {
    return fetch(`${BASE}/suites`, {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(data),
    }).then(r => handleResponse<SuiteResponse>(r));
  },

  getSuite(suiteId: string) {
    return fetch(`${BASE}/suites/${suiteId}`, { headers: headers() }).then(r => handleResponse<SuiteResponse>(r));
  },

  // Task
  listTasks(suiteId: string) {
    return fetch(`${BASE}/suites/${suiteId}/tasks`, { headers: headers() }).then(r => handleResponse<EvalTask[]>(r));
  },

  createTask(suiteId: string, data: CreateTaskRequest) {
    return fetch(`${BASE}/suites/${suiteId}/tasks`, {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(data),
    }).then(r => handleResponse<EvalTask>(r));
  },

  getTaskLifecycle(suiteId: string, taskId: string) {
    return fetch(`${BASE}/suites/${suiteId}/tasks/${taskId}/lifecycle`, { headers: headers() }).then(r => handleResponse<LifecycleEvalResponse>(r));
  },

  updateTaskLifecycle(suiteId: string, taskId: string, data: { lifecycle: Lifecycle; reason: string }) {
    return fetch(`${BASE}/suites/${suiteId}/tasks/${taskId}/lifecycle`, {
      method: "PUT",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(data),
    }).then(r => handleResponse<Record<string, unknown>>(r));
  },

  // Run
  listRuns(suiteId: string) {
    return fetch(`${BASE}/suites/${suiteId}/runs`, { headers: headers() }).then(r => handleResponse<RunResponse[]>(r));
  },

  createRun(data: CreateRunRequest) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 300_000); // 5 min timeout for model calls
    return fetch(`${BASE}/runs`, {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(data),
      signal: controller.signal,
    }).then(r => { clearTimeout(timeout); return handleResponse<RunResponse>(r); })
      .catch(e => { clearTimeout(timeout); throw e; });
  },

  getRun(runId: string) {
    return fetch(`${BASE}/runs/${runId}`, { headers: headers() }).then(r => handleResponse<RunResponse>(r));
  },

  getReportJson(runId: string) {
    return fetch(`${BASE}/runs/${runId}/report`, { headers: headers() }).then(r => handleResponse<Record<string, unknown>>(r));
  },

  getReportMarkdown(runId: string) {
    return fetch(`${BASE}/runs/${runId}/report?format=markdown`, { headers: headers() }).then(r => {
      if (!r.ok) throw new Error(`Report error: ${r.status}`);
      return r.text();
    });
  },

  // Regression + Trend
  detectRegressions(suiteId: string, currentRunId: string, baselineRunId: string) {
    return fetch(`${BASE}/regressions?suiteId=${suiteId}&currentRunId=${currentRunId}&baselineRunId=${baselineRunId}`, {
      headers: headers(),
    }).then(r => handleResponse<RegressionReport>(r));
  },

  getTrends(suiteId: string) {
    return fetch(`${BASE}/trends/${suiteId}`, { headers: headers() }).then(r => handleResponse<TrendResponse>(r));
  },

  // Transcript
  submitTranscript(data: SubmitTranscriptRequest) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 300_000);
    return fetch(`${BASE}/transcripts`, {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(data),
      signal: controller.signal,
    }).then(r => { clearTimeout(timeout); return handleResponse<Record<string, unknown>>(r); })
      .catch(e => { clearTimeout(timeout); throw e; });
  },

  getTranscript(transcriptId: string) {
    return fetch(`${BASE}/transcripts/${transcriptId}`, { headers: headers() }).then(r => handleResponse<Record<string, unknown>>(r));
  },

  // Review
  getReviewQueue(params?: { page?: number; size?: number }) {
    const q = new URLSearchParams();
    if (params?.page != null) q.set("page", String(params.page));
    if (params?.size != null) q.set("size", String(params.size));
    const qs = q.toString();
    return fetch(`${BASE}/reviews/queue${qs ? `?${qs}` : ""}`, { headers: headers() }).then(r => handleResponse<PageResponse<ReviewQueueItem>>(r));
  },

  submitReview(gradeId: string, data: SubmitReviewRequest) {
    return fetch(`${BASE}/reviews/${gradeId}/submit`, {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(data),
    }).then(r => handleResponse<ReviewResponse>(r));
  },

  getCalibration() {
    return fetch(`${BASE}/reviews/calibration`, { headers: headers() }).then(r => handleResponse<CalibrationMetrics>(r));
  },
};
