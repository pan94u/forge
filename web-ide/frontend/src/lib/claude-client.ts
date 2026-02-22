export type OodaPhase = "observe" | "orient" | "decide" | "act" | "complete";

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
    | "hitl_checkpoint";
  content?: string;
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
}

export type HitlAction = "approve" | "reject" | "modify";

export interface ChatContext {
  type: string;
  id: string;
  content?: string;
}

class ClaudeClient {
  private baseUrl: string;

  constructor(baseUrl: string = "") {
    this.baseUrl = baseUrl;
  }

  /**
   * Send a HITL response (approve/reject/modify) via HTTP POST.
   */
  async sendHitlResponse(
    sessionId: string,
    action: HitlAction,
    feedback?: string,
    modifiedPrompt?: string,
  ): Promise<void> {
    const url = `${this.baseUrl}/api/chat/sessions/${sessionId}/hitl`;
    await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action, feedback, modifiedPrompt }),
    });
  }

  /**
   * Stream a message to the AI agent via HTTP SSE.
   * Model name and workspaceId are passed in the request body.
   */
  async streamMessage(
    sessionId: string,
    message: string,
    contexts: ChatContext[],
    onEvent: (event: StreamEvent) => void,
    signal?: AbortSignal,
    workspaceId?: string,
    model?: string,
  ): Promise<void> {
    const url = `${this.baseUrl}/api/chat/sessions/${sessionId}/stream`;

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify({
        type: "message",
        content: message,
        contexts,
        workspaceId: workspaceId ?? "",
        model: model ?? "claude-sonnet-4-6",
      }),
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

        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";

        for (const line of lines) {
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
              if (parsed.type === "done") return;
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
    contexts: ChatContext[],
  ): Promise<{
    content: string;
    toolCalls: Array<{ name: string; output: string }>;
  }> {
    const url = `${this.baseUrl}/api/chat/sessions/${sessionId}/messages`;

    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
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
  async getMessages(sessionId: string): Promise<
    Array<{
      id: string;
      role: "user" | "assistant";
      content: string;
      timestamp: string;
    }>
  > {
    const url = `${this.baseUrl}/api/chat/sessions/${sessionId}/messages`;

    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to get messages: ${response.status}`);
    }

    return response.json();
  }
}

export const claudeClient = new ClaudeClient();
