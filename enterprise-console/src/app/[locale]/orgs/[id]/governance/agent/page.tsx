"use client";

import { useState, useRef } from "react";
import { useParams } from "next/navigation";
import { ArrowLeft, Bot, Send } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

interface AnalysisMessage {
  role: "user" | "assistant";
  content: string;
  timestamp: Date;
}

export default function GovernanceAgentPage() {
  const { id } = useParams<{ id: string }>();
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<AnalysisMessage[]>([]);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleAnalyze = async () => {
    if (!input.trim() || isAnalyzing) return;

    const userMessage: AnalysisMessage = {
      role: "user",
      content: input.trim(),
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsAnalyzing(true);

    try {
      // 通过 Forge /api/chat/sessions 创建 session
      const sessionRes = await fetch("/api/chat/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title: `治理分析: ${userMessage.content.slice(0, 50)}`,
          workspaceId: null,
        }),
      });

      if (!sessionRes.ok) throw new Error("Failed to create session");
      const session = await sessionRes.json();

      // SSE stream
      const streamRes = await fetch(
        `/api/chat/sessions/${session.id}/stream`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ content: userMessage.content }),
        }
      );

      if (!streamRes.ok) throw new Error("Failed to start stream");
      const reader = streamRes.body?.getReader();
      if (!reader) throw new Error("No response body");

      const decoder = new TextDecoder();
      let assistantContent = "";

      // Add empty assistant message for streaming
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: "", timestamp: new Date() },
      ]);

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        const lines = chunk.split("\n");

        for (const line of lines) {
          if (line.startsWith("data:")) {
            const dataStr = line.slice(5).trim();
            if (!dataStr || dataStr === "[DONE]") continue;
            try {
              const data = JSON.parse(dataStr);
              if (data.type === "text_delta" && data.delta) {
                assistantContent += data.delta;
                setMessages((prev) => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    ...updated[updated.length - 1],
                    content: assistantContent,
                  };
                  return updated;
                });
              }
            } catch {
              // ignore parse errors
            }
          }
        }
      }
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: `分析出错：${error instanceof Error ? error.message : "未知错误"}`,
          timestamp: new Date(),
        },
      ]);
    } finally {
      setIsAnalyzing(false);
    }
  };

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <div>
          <h1 className="text-xl font-bold text-foreground">治理 AI 分析助手</h1>
          <p className="text-sm text-muted-foreground">
            描述你的治理分析需求，AI 将为你分析并生成报告
          </p>
        </div>
      </div>

      {/* Chat history */}
      <div className="mb-4 space-y-4 min-h-[200px]">
        {messages.length === 0 && (
          <div className="rounded-lg border border-dashed border-border p-8 text-center">
            <Bot size={32} className="mx-auto mb-3 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              输入你的治理分析需求，例如：
            </p>
            <div className="mt-2 space-y-1">
              {[
                "分析 Q1 预算使用情况",
                "识别高风险团队成员",
                "评估当前安全态势",
              ].map((example) => (
                <button
                  key={example}
                  onClick={() => setInput(example)}
                  className="block w-full text-xs text-primary hover:underline py-0.5"
                >
                  {example}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg, i) => (
          <div
            key={i}
            className={`flex gap-3 ${msg.role === "user" ? "justify-end" : "justify-start"}`}
          >
            {msg.role === "assistant" && (
              <div className="mt-1 h-7 w-7 flex-shrink-0 rounded-full bg-primary/10 flex items-center justify-center">
                <Bot size={14} className="text-primary" />
              </div>
            )}
            <div
              className={`max-w-[80%] rounded-lg px-4 py-3 text-sm ${
                msg.role === "user"
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-foreground"
              }`}
            >
              {msg.content || (
                <span className="flex items-center gap-1 text-muted-foreground">
                  <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-current" />
                  <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-current [animation-delay:0.1s]" />
                  <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-current [animation-delay:0.2s]" />
                </span>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Input area */}
      <Card title="">
        <div className="flex gap-3">
          <textarea
            ref={textareaRef}
            className="flex-1 resize-none rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none focus:ring-1 focus:ring-ring"
            rows={3}
            placeholder="例：分析 Q1 架构风险，识别高风险团队，生成预算报告..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
                handleAnalyze();
              }
            }}
            disabled={isAnalyzing}
          />
          <div className="flex flex-col justify-end">
            <Button
              onClick={handleAnalyze}
              disabled={!input.trim() || isAnalyzing}
              loading={isAnalyzing}
            >
              <Send size={14} />
              {isAnalyzing ? "分析中..." : "开始分析"}
            </Button>
          </div>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">Ctrl+Enter 发送</p>
      </Card>
    </div>
  );
}
