"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Plus, Trash2, TestTube } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/navigation";
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
  const t = useTranslations("dbConnections");
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
  const [testing, setTesting] = useState<string | null>(null);

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
      setName(""); setJdbcUrl(""); setUsername(""); setPassword("");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (connId: string) => api.dbConnections.delete(id, connId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["orgs", id, "db-connections"] }),
  });

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
            <h1 className="text-xl font-bold text-foreground">{t("title")}</h1>
            <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
          </div>
        </div>
        <Button onClick={() => setShowForm(true)} size="sm">
          <Plus size={14} />
          {t("addBtn")}
        </Button>
      </div>

      {showForm && (
        <Card title={t("newTitle")} className="mb-4 max-w-lg">
          <div className="space-y-3">
            <Input label={t("nameLabel")} placeholder={t("namePlaceholder")} value={name} onChange={(e) => setName(e.target.value)} required />
            <Input label={t("jdbcUrlLabel")} placeholder={t("jdbcUrlPlaceholder")} value={jdbcUrl} onChange={(e) => setJdbcUrl(e.target.value)} required />
            <Input label={t("usernameLabel")} placeholder={t("usernamePlaceholder")} value={username} onChange={(e) => setUsername(e.target.value)} />
            <Input label={t("passwordLabel")} type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground">{t("accessLevelLabel")}</label>
              <select
                className="rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
                value={accessLevel}
                onChange={(e) => setAccessLevel(e.target.value)}
              >
                <option value="FULL_READ">{t("accessFull")}</option>
                <option value="READ_ONLY">{t("accessRead")}</option>
                <option value="SCHEMA_ONLY">{t("accessSchema")}</option>
              </select>
            </div>
            <div className="flex gap-2 pt-2">
              <Button loading={createMutation.isPending} disabled={!name || !jdbcUrl} onClick={() => createMutation.mutate()}>
                {t("createBtn")}
              </Button>
              <Button variant="secondary" onClick={() => setShowForm(false)}>{t("cancel")}</Button>
            </div>
          </div>
        </Card>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : connections.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border py-16 text-center">
          <p className="text-muted-foreground">{t("noConnections")}</p>
          <Button size="sm" className="mt-3" onClick={() => setShowForm(true)}>
            <Plus size={13} />
            {t("addFirst")}
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
                      <p className="font-medium text-foreground">{conn.name}</p>
                      <Badge color={conn.accessLevel === "FULL_READ" ? "blue" : conn.accessLevel === "READ_ONLY" ? "green" : "gray"}>
                        {conn.accessLevel.toLowerCase().replace("_", " ")}
                      </Badge>
                    </div>
                    <p className="mt-1 font-mono text-xs text-muted-foreground truncate">{conn.jdbcUrl}</p>
                    {conn.username && (
                      <p className="mt-0.5 text-xs text-muted-foreground">{t("userLabel")}{conn.username}</p>
                    )}
                    {testResult && (
                      <div className={`mt-2 flex items-center gap-1.5 rounded px-2 py-1 text-xs ${testResult.success ? "bg-green-900/30 text-green-300" : "bg-destructive/20 text-destructive-foreground"}`}>
                        {testResult.success ? "✓" : "✗"} {testResult.message}
                      </div>
                    )}
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <Button variant="secondary" size="sm" loading={testing === conn.id} onClick={() => handleTest(conn.id)}>
                      <TestTube size={13} />
                      {t("testBtn")}
                    </Button>
                    <Button
                      variant="ghost" size="sm"
                      className="text-destructive hover:text-destructive hover:bg-destructive/10"
                      onClick={() => { if (confirm(t("deleteConfirm", { name: conn.name }))) deleteMutation.mutate(conn.id); }}
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
