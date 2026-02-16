"use client";

import React, { useState } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import {
  ChevronRight,
  ChevronDown,
  Send,
  Copy,
  Check,
  Globe,
  Loader2,
} from "lucide-react";

interface ApiEndpoint {
  method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  path: string;
  summary: string;
  description: string;
  parameters: Array<{
    name: string;
    in: "path" | "query" | "header" | "body";
    required: boolean;
    type: string;
    description: string;
  }>;
  requestBody?: {
    contentType: string;
    schema: string;
    example: string;
  };
  responses: Array<{
    status: number;
    description: string;
    example?: string;
  }>;
}

interface ApiService {
  id: string;
  name: string;
  baseUrl: string;
  description: string;
  endpoints: ApiEndpoint[];
}

const methodColors: Record<string, string> = {
  GET: "bg-green-500/10 text-green-400 border-green-500/30",
  POST: "bg-blue-500/10 text-blue-400 border-blue-500/30",
  PUT: "bg-yellow-500/10 text-yellow-400 border-yellow-500/30",
  DELETE: "bg-red-500/10 text-red-400 border-red-500/30",
  PATCH: "bg-purple-500/10 text-purple-400 border-purple-500/30",
};

function EndpointCard({
  endpoint,
  baseUrl,
}: {
  endpoint: ApiEndpoint;
  baseUrl: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const [tryItOpen, setTryItOpen] = useState(false);
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [requestBody, setRequestBody] = useState(endpoint.requestBody?.example ?? "");
  const [copied, setCopied] = useState(false);

  const tryItMutation = useMutation({
    mutationFn: async () => {
      let url = `${baseUrl}${endpoint.path}`;

      // Replace path params
      endpoint.parameters
        .filter((p) => p.in === "path")
        .forEach((p) => {
          url = url.replace(`{${p.name}}`, paramValues[p.name] ?? "");
        });

      // Add query params
      const queryParams = endpoint.parameters
        .filter((p) => p.in === "query" && paramValues[p.name])
        .map((p) => `${p.name}=${encodeURIComponent(paramValues[p.name])}`)
        .join("&");
      if (queryParams) url += `?${queryParams}`;

      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };
      endpoint.parameters
        .filter((p) => p.in === "header" && paramValues[p.name])
        .forEach((p) => {
          headers[p.name] = paramValues[p.name];
        });

      const res = await fetch("/api/knowledge/apis/try", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          method: endpoint.method,
          url,
          headers,
          body: ["POST", "PUT", "PATCH"].includes(endpoint.method)
            ? requestBody
            : undefined,
        }),
      });

      return res.json();
    },
  });

  const handleCopyUrl = async () => {
    await navigator.clipboard.writeText(`${baseUrl}${endpoint.path}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="border border-border rounded-md">
      {/* Endpoint Header */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center gap-3 p-3 text-left hover:bg-accent/50"
      >
        {expanded ? (
          <ChevronDown className="h-4 w-4 flex-shrink-0 text-muted-foreground" />
        ) : (
          <ChevronRight className="h-4 w-4 flex-shrink-0 text-muted-foreground" />
        )}
        <span
          className={`rounded border px-2 py-0.5 text-xs font-bold ${
            methodColors[endpoint.method]
          }`}
        >
          {endpoint.method}
        </span>
        <span className="font-mono text-sm">{endpoint.path}</span>
        <span className="flex-1 truncate text-xs text-muted-foreground">
          {endpoint.summary}
        </span>
      </button>

      {/* Expanded Details */}
      {expanded && (
        <div className="border-t border-border p-4 space-y-4">
          {/* Description */}
          {endpoint.description && (
            <p className="text-sm text-muted-foreground">
              {endpoint.description}
            </p>
          )}

          {/* URL with copy */}
          <div className="flex items-center gap-2 rounded bg-muted p-2 font-mono text-xs">
            <span className="flex-1 truncate">
              {baseUrl}
              {endpoint.path}
            </span>
            <button onClick={handleCopyUrl} className="flex-shrink-0">
              {copied ? (
                <Check className="h-3.5 w-3.5 text-green-400" />
              ) : (
                <Copy className="h-3.5 w-3.5 text-muted-foreground hover:text-foreground" />
              )}
            </button>
          </div>

          {/* Parameters */}
          {endpoint.parameters.length > 0 && (
            <div>
              <h5 className="mb-2 text-xs font-semibold uppercase text-muted-foreground">
                Parameters
              </h5>
              <div className="space-y-2">
                {endpoint.parameters.map((param) => (
                  <div key={param.name} className="flex items-start gap-3 text-sm">
                    <div className="w-32 flex-shrink-0">
                      <span className="font-mono font-medium">
                        {param.name}
                      </span>
                      {param.required && (
                        <span className="ml-1 text-xs text-destructive">*</span>
                      )}
                      <div className="text-xs text-muted-foreground">
                        {param.in} - {param.type}
                      </div>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {param.description}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Responses */}
          <div>
            <h5 className="mb-2 text-xs font-semibold uppercase text-muted-foreground">
              Responses
            </h5>
            <div className="space-y-2">
              {endpoint.responses.map((resp) => (
                <div key={resp.status} className="text-sm">
                  <div className="flex items-center gap-2">
                    <span
                      className={`rounded px-1.5 py-0.5 text-xs font-bold ${
                        resp.status < 300
                          ? "bg-green-500/10 text-green-400"
                          : resp.status < 400
                            ? "bg-yellow-500/10 text-yellow-400"
                            : "bg-red-500/10 text-red-400"
                      }`}
                    >
                      {resp.status}
                    </span>
                    <span className="text-muted-foreground">
                      {resp.description}
                    </span>
                  </div>
                  {resp.example && (
                    <pre className="mt-1 overflow-x-auto rounded bg-[#1e1e1e] p-2 text-xs font-mono">
                      {resp.example}
                    </pre>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Try It Out */}
          <div className="border-t border-border pt-4">
            <button
              onClick={() => setTryItOpen(!tryItOpen)}
              className="flex items-center gap-2 text-sm font-medium text-primary hover:underline"
            >
              <Send className="h-3.5 w-3.5" />
              Try it out
            </button>

            {tryItOpen && (
              <div className="mt-3 space-y-3">
                {/* Parameter inputs */}
                {endpoint.parameters.map((param) => (
                  <div key={param.name}>
                    <label className="block text-xs font-medium">
                      {param.name}
                      {param.required && (
                        <span className="text-destructive"> *</span>
                      )}
                    </label>
                    <input
                      className="mt-1 w-full rounded border border-input bg-background px-2 py-1 text-sm font-mono"
                      placeholder={`${param.type} (${param.in})`}
                      value={paramValues[param.name] ?? ""}
                      onChange={(e) =>
                        setParamValues((prev) => ({
                          ...prev,
                          [param.name]: e.target.value,
                        }))
                      }
                    />
                  </div>
                ))}

                {/* Request body */}
                {endpoint.requestBody && (
                  <div>
                    <label className="block text-xs font-medium">
                      Request Body ({endpoint.requestBody.contentType})
                    </label>
                    <textarea
                      className="mt-1 w-full rounded border border-input bg-background px-2 py-1 text-xs font-mono"
                      rows={6}
                      value={requestBody}
                      onChange={(e) => setRequestBody(e.target.value)}
                    />
                  </div>
                )}

                {/* Execute */}
                <button
                  onClick={() => tryItMutation.mutate()}
                  disabled={tryItMutation.isPending}
                  className="flex items-center gap-2 rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                >
                  {tryItMutation.isPending ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Send className="h-3.5 w-3.5" />
                  )}
                  Execute
                </button>

                {/* Response */}
                {tryItMutation.data && (
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground">
                      Response
                    </label>
                    <pre className="mt-1 overflow-x-auto rounded bg-[#1e1e1e] p-3 text-xs font-mono">
                      {JSON.stringify(tryItMutation.data, null, 2)}
                    </pre>
                  </div>
                )}

                {tryItMutation.error && (
                  <div className="rounded border border-destructive/50 bg-destructive/10 p-2 text-xs text-destructive">
                    {(tryItMutation.error as Error).message}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export function ApiExplorer() {
  const [expandedServices, setExpandedServices] = useState<Set<string>>(
    new Set()
  );

  const { data: services, isLoading } = useQuery<ApiService[]>({
    queryKey: ["api-catalog"],
    queryFn: async () => {
      const res = await fetch("/api/knowledge/apis");
      if (!res.ok) throw new Error("Failed to fetch API catalog");
      return res.json();
    },
  });

  const toggleService = (id: string) => {
    setExpandedServices((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  if (isLoading) {
    return (
      <div className="p-6 space-y-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-20 animate-pulse rounded-md bg-muted" />
        ))}
      </div>
    );
  }

  return (
    <div className="p-6">
      <div className="flex items-center gap-3 mb-6">
        <Globe className="h-5 w-5 text-primary" />
        <h2 className="text-lg font-semibold">API Explorer</h2>
        <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
          {services?.reduce((acc, s) => acc + s.endpoints.length, 0) ?? 0}{" "}
          endpoints
        </span>
      </div>

      <div className="space-y-4">
        {services && services.length > 0 ? (
          services.map((service) => (
            <div key={service.id} className="rounded-lg border border-border">
              <button
                onClick={() => toggleService(service.id)}
                className="flex w-full items-center gap-3 p-4 text-left hover:bg-accent/50"
              >
                {expandedServices.has(service.id) ? (
                  <ChevronDown className="h-4 w-4 text-muted-foreground" />
                ) : (
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                )}
                <div className="flex-1">
                  <h3 className="font-semibold">{service.name}</h3>
                  <p className="text-xs text-muted-foreground">
                    {service.description} - {service.baseUrl}
                  </p>
                </div>
                <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  {service.endpoints.length} endpoints
                </span>
              </button>

              {expandedServices.has(service.id) && (
                <div className="border-t border-border p-4 space-y-2">
                  {service.endpoints.map((endpoint, idx) => (
                    <EndpointCard
                      key={`${endpoint.method}-${endpoint.path}-${idx}`}
                      endpoint={endpoint}
                      baseUrl={service.baseUrl}
                    />
                  ))}
                </div>
              )}
            </div>
          ))
        ) : (
          <div className="flex items-center justify-center py-12 text-muted-foreground">
            <div className="text-center">
              <Globe className="mx-auto h-8 w-8 opacity-50" />
              <p className="mt-3 text-sm">No API services found</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
