export interface StreamEvent {
  type:
    | "thinking"
    | "content"
    | "tool_use"
    | "tool_result"
    | "error"
    | "done";
  content?: string;
  toolCallId?: string;
  toolName?: string;
  toolInput?: Record<string, unknown>;
}

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
   * Stream a message to the Claude agent via WebSocket.
   * Falls back to HTTP SSE if WebSocket is unavailable.
   */
  async streamMessage(
    sessionId: string,
    message: string,
    contexts: ChatContext[],
    onEvent: (event: StreamEvent) => void,
    signal?: AbortSignal
  ): Promise<void> {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = this.baseUrl || window.location.host;
    const wsUrl = `${protocol}//${host}/ws/chat/${sessionId}`;

    return new Promise<void>((resolve, reject) => {
      if (signal?.aborted) {
        reject(new DOMException("Aborted", "AbortError"));
        return;
      }

      let ws: WebSocket;

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
        ws.send(
          JSON.stringify({
            type: "message",
            content: message,
            contexts,
          })
        );
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as StreamEvent;
          onEvent(data);

          if (data.type === "done") {
            cleanup();
            resolve();
          }
        } catch {
          onEvent({ type: "content", content: event.data });
        }
      };

      ws.onerror = () => {
        // Try HTTP fallback on WebSocket error
        cleanup();
        this.streamMessageHttp(sessionId, message, contexts, onEvent, signal)
          .then(resolve)
          .catch(reject);
      };

      ws.onclose = (event) => {
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
      },
      body: JSON.stringify({ message, contexts }),
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
          if (line.startsWith("data: ")) {
            const data = line.slice(6).trim();
            if (data === "[DONE]") {
              onEvent({ type: "done" });
              return;
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
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, contexts }),
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

    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to get messages: ${response.status}`);
    }

    return response.json();
  }
}

export const claudeClient = new ClaudeClient();
