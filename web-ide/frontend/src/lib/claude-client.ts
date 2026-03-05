export type OodaPhase = "observe" | "orient" | "decide" | "act" | "complete";

export interface PlanTask {
  id: string;
  title: string;
  files: string[];
  successCriteria?: string;
  estimatedLines?: number;
  status: "pending" | "in_progress" | "done" | "failed" | "blocked";
}

export interface PlanQuestion {
  type: "choice" | "text";
  question: string;
  options?: string[];
}

export interface IntentOption {
  id: string;
  label: string;
  description: string;
}

export interface StreamEvent {
  type:
    | "thinking"
    | "content"
    | "tool_use"
    | "tool_use_start"
    | "tool_result"
    | "error"
    | "done"
    | "profile_active"
    | "ooda_phase"
    | "file_changed"
    | "sub_step"
    | "baseline_check"
    | "hitl_checkpoint"
    | "context_usage"
    | "context_warning"
    | "intent_confirmation"
    | "skills_activated"
    | "git_confirm"
    | "plan_ready"
    | "plan_task_update"
    | "plan_ask"
    | "plan_summary";
  content?: string;
  skills?: string[];
  toolCallId?: string;
  toolName?: string;
  toolInput?: Record<string, unknown>;
  activeProfile?: string;
  loadedSkills?: string[];
  routingReason?: string;
  confidence?: number;
  phase?: OodaPhase;
  path?: string;
  action?: string;
  // sub_step fields
  message?: string;
  timestamp?: string;
  // ooda_phase enhanced fields
  detail?: string;
  turn?: number;
  maxTurns?: number;
  // baseline_check fields
  status?: string;
  attempt?: number;
  baselines?: string[];
  summary?: string;
  // hitl_checkpoint fields
  profile?: string;
  checkpoint?: string;
  deliverables?: string[];
  baselineResults?: Array<{ name: string; status: string; output?: string }>;
  timeoutSeconds?: number;
  hitlFeedback?: string;
  // context_usage fields
  tokensUsed?: number;
  tokenBudget?: number;
  compressionPhase?: number;
  // intent_confirmation fields
  currentProfile?: string;
  reason?: string;
  options?: IntentOption[];
  // git_confirm fields
  gitConfirmTool?: string;
  gitConfirmPreview?: string;
  // context_warning fields
  totalTokens?: number;
  recommendation?: string;
  // plan_ready fields
  planId?: string;
  tasks?: PlanTask[];
  // plan_task_update fields
  taskId?: string;
  // plan_ask fields
  askId?: string;
  questions?: PlanQuestion[];
  // plan_summary fields
  suggestions?: string[];
}

export type HitlAction = "approve" | "reject" | "modify";

export interface ChatContext {
  type: string;
  id: string;
  content?: string;
}

function getAuthHeaders(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("forge_access_token");
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

class ClaudeClient {
  private baseUrl: string;
  private activeWs: WebSocket | null = null;

  constructor(baseUrl: string = "") {
    this.baseUrl = baseUrl;
  }

  /**
   * Send a git operation confirmation response via the active WebSocket.
   * Called when user clicks approve/cancel on the git confirmation card.
   */
  sendGitConfirmResponse(approved: boolean): void {
    if (!this.activeWs || this.activeWs.readyState !== WebSocket.OPEN) {
      console.error("Cannot send git confirm response: WebSocket not connected");
      return;
    }
    this.activeWs.send(JSON.stringify({
      type: "git_confirm_response",
      approved,
    }));
  }

  /**
   * Send an intent confirmation response via the active WebSocket.
   */
  sendIntentResponse(selectedProfile: string): void {
    if (!this.activeWs || this.activeWs.readyState !== WebSocket.OPEN) {
      console.error("Cannot send intent response: WebSocket not connected");
      return;
    }
    this.activeWs.send(JSON.stringify({
      type: "intent_response",
      selectedProfile,
    }));
  }

  /**
   * Send a HITL response (approve/reject/modify) via the active WebSocket.
   */
  sendHitlResponse(action: HitlAction, feedback?: string, modifiedPrompt?: string): void {
    if (!this.activeWs || this.activeWs.readyState !== WebSocket.OPEN) {
      console.error("Cannot send HITL response: WebSocket not connected");
      return;
    }
    this.activeWs.send(JSON.stringify({
      type: "hitl_response",
      action,
      feedback,
      modifiedPrompt,
    }));
  }

  /**
   * Stream a message to the Claude agent via WebSocket.
   * Falls back to HTTP SSE if WebSocket is unavailable.
   */
  async streamMessage(
    sessionId: string,
    message: string,
    contexts: ChatContext[],
    onEvent: (event: StreamEvent) => void,
    signal?: AbortSignal,
    workspaceId?: string,
    modelId?: string
  ): Promise<void> {
    // In local dev, Next.js rewrites cannot upgrade ws:// protocol.
    // Connect directly to the backend WS port to avoid the proxy gap.
    const isDev = process.env.NODE_ENV === "development";
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = isDev ? "localhost:8080" : (this.baseUrl || window.location.host);
    const wsUrl = `${protocol}//${host}/ws/chat/${sessionId}`;

    return new Promise<void>((resolve, reject) => {
      if (signal?.aborted) {
        reject(new DOMException("Aborted", "AbortError"));
        return;
      }

      let ws: WebSocket;
      // Flag to prevent onclose from resolving after HTTP fallback takes over
      let httpFallbackStarted = false;

      try {
        ws = new WebSocket(wsUrl);
      } catch {
        // Fall back to HTTP SSE
        this.streamMessageHttp(sessionId, message, contexts, onEvent, signal)
          .then(resolve)
          .catch(reject);
        return;
      }

      const cleanup = () => {
        if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
          ws.close();
        }
      };

      if (signal) {
        signal.addEventListener("abort", () => {
          cleanup();
          reject(new DOMException("Aborted", "AbortError"));
        });
      }

      ws.onopen = () => {
        this.activeWs = ws;
        ws.send(
          JSON.stringify({
            type: "message",
            content: message,
            contexts,
            workspaceId,
            // BUG-066: only send modelId when explicitly selected; empty → backend uses MODEL_PROVIDER default
            ...(modelId ? { modelId } : {}),
          })
        );
      };

      let errorTimeout: ReturnType<typeof setTimeout> | null = null;

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as StreamEvent;
          onEvent(data);

          if (data.type === "done") {
            if (errorTimeout) clearTimeout(errorTimeout);
            cleanup();
            resolve();
          }

          // If we get an error event, set a 5s safety timeout
          // in case the server fails to send a "done" event
          if (data.type === "error" && !errorTimeout) {
            errorTimeout = setTimeout(() => {
              cleanup();
              resolve();
            }, 5000);
          }
        } catch {
          onEvent({ type: "content", content: event.data });
        }
      };

      ws.onerror = () => {
        // Try HTTP fallback on WebSocket error
        httpFallbackStarted = true;
        cleanup();
        this.streamMessageHttp(sessionId, message, contexts, onEvent, signal)
          .then(resolve)
          .catch(reject);
      };

      ws.onclose = (event) => {
        this.activeWs = null;
        // Don't resolve if HTTP fallback has taken over — it owns resolve/reject
        if (httpFallbackStarted) return;
        if (!event.wasClean) {
          // Connection closed unexpectedly
          onEvent({ type: "done" });
        }
        resolve();
      };
    });
  }

  /**
   * HTTP SSE fallback for streaming messages.
   */
  private async streamMessageHttp(
    sessionId: string,
    message: string,
    contexts: ChatContext[],
    onEvent: (event: StreamEvent) => void,
    signal?: AbortSignal
  ): Promise<void> {
    const url = `${this.baseUrl}/api/chat/sessions/${sessionId}/stream`;

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ type: "message", content: message, contexts }),
      signal,
    });

    if (!response.ok) {
      throw new Error(`Chat request failed: ${response.status}`);
    }

    const reader = response.body?.getReader();
    if (!reader) {
      throw new Error("No response body");
    }

    const decoder = new TextDecoder();
    let buffer = "";

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // Parse SSE events
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";

        for (const line of lines) {
          // Handle both "data:" and "data: " (Spring SSE omits the space)
          if (line.startsWith("data:")) {
            const data = line.startsWith("data: ")
              ? line.slice(6).trim()
              : line.slice(5).trim();
            if (!data || data === "[DONE]") {
              if (data === "[DONE]") {
                onEvent({ type: "done" });
                return;
              }
              continue;
            }
            try {
              const parsed = JSON.parse(data) as StreamEvent;
              onEvent(parsed);
            } catch {
              onEvent({ type: "content", content: data });
            }
          }
        }
      }
    } finally {
      reader.releaseLock();
    }

    onEvent({ type: "done" });
  }

  /**
   * Send a non-streaming message (for simple requests).
   */
  async sendMessage(
    sessionId: string,
    message: string,
    contexts: ChatContext[]
  ): Promise<{ content: string; toolCalls: Array<{ name: string; output: string }> }> {
    const url = `${this.baseUrl}/api/chat/sessions/${sessionId}/messages`;

    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...getAuthHeaders() },
      body: JSON.stringify({ type: "message", content: message, contexts }),
    });

    if (!response.ok) {
      throw new Error(`Chat request failed: ${response.status}`);
    }

    return response.json();
  }

  /**
   * Get message history for a session.
   */
  async getMessages(
    sessionId: string
  ): Promise<
    Array<{
      id: string;
      role: "user" | "assistant";
      content: string;
      timestamp: string;
    }>
  > {
    const url = `${this.baseUrl}/api/chat/sessions/${sessionId}/messages`;

    const response = await fetch(url, { headers: getAuthHeaders() });
    if (!response.ok) {
      throw new Error(`Failed to get messages: ${response.status}`);
    }

    return response.json();
  }
}

export const claudeClient = new ClaudeClient();
