"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Plus, Trash2, TestTube, X } from "lucide-react";
import Link from "next/link";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";

interface TestResult {
  success: boolean;
  message: string;
}

export default function DbConnectionsPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();

  const { data: connections = [], isLoading } = useQuery({
    queryKey: ["orgs", id, "db-connections"],
    queryFn: () => api.dbConnections.list(id),
  });

  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [jdbcUrl, setJdbcUrl] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [accessLevel, setAccessLevel] = useState("FULL_READ");
  const [testResults, setTestResults] = useState<Record<string, TestResult>>({});

  const createMutation = useMutation({
    mutationFn: () =>
      api.dbConnections.create(id, {
        name,
        jdbcUrl,
        username: username || undefined,
        password: password || undefined,
        accessLevel,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orgs", id, "db-connections"] });
      setShowForm(false);
      setName("");
      setJdbcUrl("");
      setUsername("");
      setPassword("");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (connId: string) => api.dbConnections.delete(id, connId),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["orgs", id, "db-connections"] }),
  });

  const [testing, setTesting] = useState<string | null>(null);

  async function handleTest(connId: string) {
    setTesting(connId);
    try {
      const result = await api.dbConnections.test(id, connId);
      setTestResults((prev) => ({ ...prev, [connId]: result }));
    } finally {
      setTesting(null);
    }
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link href={`/orgs/${id}`}>
            <Button variant="ghost" size="sm">
              <ArrowLeft size={14} />
            </Button>
          </Link>
          <div>
            <h1 className="text-xl font-bold text-white">Database Connections</h1>
            <p className="text-sm text-gray-400">
              Manage database connections for this organization
            </p>
          </div>
        </div>
        <Button onClick={() => setShowForm(true)} size="sm">
          <Plus size={14} />
          Add Connection
        </Button>
      </div>

      {showForm && (
        <Card title="New Database Connection" className="mb-4 max-w-lg">
          <div className="space-y-3">
            <Input
              label="Name"
              placeholder="Production PostgreSQL"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <Input
              label="JDBC URL"
              placeholder="jdbc:postgresql://host:5432/dbname"
              value={jdbcUrl}
              onChange={(e) => setJdbcUrl(e.target.value)}
              required
            />
            <Input
              label="Username"
              placeholder="db_user"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-gray-400">
                Access Level
              </label>
              <select
                className="rounded-md border border-gray-600 bg-gray-800 px-3 py-2 text-sm text-gray-100"
                value={accessLevel}
                onChange={(e) => setAccessLevel(e.target.value)}
              >
                <option value="FULL_READ">Full Read</option>
                <option value="READ_ONLY">Read Only</option>
                <option value="SCHEMA_ONLY">Schema Only</option>
              </select>
            </div>
            <div className="flex gap-2 pt-2">
              <Button
                loading={createMutation.isPending}
                disabled={!name || !jdbcUrl}
                onClick={() => createMutation.mutate()}
              >
                Create Connection
              </Button>
              <Button
                variant="secondary"
                onClick={() => setShowForm(false)}
              >
                Cancel
              </Button>
            </div>
          </div>
        </Card>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
        </div>
      ) : connections.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-gray-700 py-16 text-center">
          <p className="text-gray-400">No database connections configured</p>
          <Button
            size="sm"
            className="mt-3"
            onClick={() => setShowForm(true)}
          >
            <Plus size={13} />
            Add First Connection
          </Button>
        </div>
      ) : (
        <div className="space-y-3">
          {connections.map((conn) => {
            const testResult = testResults[conn.id];
            return (
              <Card key={conn.id}>
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="font-medium text-gray-200">{conn.name}</p>
                      <Badge
                        color={
                          conn.accessLevel === "FULL_READ"
                            ? "blue"
                            : conn.accessLevel === "READ_ONLY"
                            ? "green"
                            : "gray"
                        }
                      >
                        {conn.accessLevel.toLowerCase().replace("_", " ")}
                      </Badge>
                    </div>
                    <p className="mt-1 font-mono text-xs text-gray-500 truncate">
                      {conn.jdbcUrl}
                    </p>
                    {conn.username && (
                      <p className="mt-0.5 text-xs text-gray-500">
                        User: {conn.username}
                      </p>
                    )}
                    {testResult && (
                      <div
                        className={`mt-2 flex items-center gap-1.5 rounded px-2 py-1 text-xs ${
                          testResult.success
                            ? "bg-green-900/30 text-green-300"
                            : "bg-red-900/30 text-red-300"
                        }`}
                      >
                        {testResult.success ? "✓" : "✗"} {testResult.message}
                      </div>
                    )}
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={testing === conn.id}
                      onClick={() => handleTest(conn.id)}
                    >
                      <TestTube size={13} />
                      Test
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-red-400 hover:text-red-300"
                      onClick={() => {
                        if (confirm(`Delete connection "${conn.name}"?`)) {
                          deleteMutation.mutate(conn.id);
                        }
                      }}
                    >
                      <Trash2 size={13} />
                    </Button>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
