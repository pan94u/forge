"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import {
  ArrowLeft,
  Users,
  HardDrive,
  Settings,
  Database,
  Key,
  Trash2,
  Plus,
  X,
  Link as LinkIcon,
  UserPlus,
  BarChart2,
  ClipboardList,
  Gauge,
  ShieldCheck,
  FlaskConical,
} from "lucide-react";
import { Link, useRouter } from "@/navigation";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { InviteModal } from "@/components/InviteModal";
import { useIsSystemAdmin, useOrgRole } from "@/lib/session";

type Tab = "overview" | "members" | "workspaces";

export default function OrgDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const t = useTranslations("orgDetail");
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>("overview");
  const [showInvite, setShowInvite] = useState(false);
  const isAdmin = useIsSystemAdmin();

  const { data: org, isLoading } = useQuery({
    queryKey: ["orgs", id],
    queryFn: () => api.orgs.get(id),
  });
  const { data: members = [] } = useQuery({
    queryKey: ["orgs", id, "members"],
    queryFn: () => api.members.list(id),
    enabled: tab === "members",
  });
  const { data: boundWorkspaces = [] } = useQuery({
    queryKey: ["orgs", id, "workspaces"],
    queryFn: () => api.workspaces.listByOrg(id),
    enabled: tab === "workspaces",
  });
  const { data: allWorkspaces = [] } = useQuery({
    queryKey: ["workspaces"],
    queryFn: api.workspaces.listAll,
    enabled: tab === "workspaces",
  });

  const orgRole = useOrgRole(members);
  const canInvite = isAdmin || orgRole === "OWNER" || orgRole === "ADMIN";

  const [editName, setEditName] = useState("");
  const [editDesc, setEditDesc] = useState("");
  const [editing, setEditing] = useState(false);
  const [newUserId, setNewUserId] = useState("");
  const [newRole, setNewRole] = useState("MEMBER");
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [selectedWsId, setSelectedWsId] = useState("");

  const updateMutation = useMutation({
    mutationFn: (req: { name: string; description: string }) =>
      api.orgs.update(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orgs", id] });
      qc.invalidateQueries({ queryKey: ["orgs"] });
      setEditing(false);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.orgs.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orgs"] });
      router.push("/orgs");
    },
  });

  const addMemberMutation = useMutation({
    mutationFn: () => api.members.add(id, { userId: newUserId, role: newRole }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orgs", id, "members"] });
      setNewUserId("");
      setMutationError(null);
    },
    onError: (err: Error) => setMutationError(err.message),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) => api.members.remove(id, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["orgs", id, "members"] }),
  });

  const bindMutation = useMutation({
    mutationFn: (wsId: string) => api.workspaces.bind(id, wsId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orgs", id, "workspaces"] });
      setSelectedWsId("");
    },
  });

  const unbindMutation = useMutation({
    mutationFn: (wsId: string) => api.workspaces.unbind(id, wsId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["orgs", id, "workspaces"] }),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!org) {
    return (
      <div className="text-center py-20">
        <p className="text-muted-foreground">{t("notFound")}</p>
        <Link href="/orgs" className="mt-4 inline-block">
          <Button variant="secondary">{t("backToOrgs")}</Button>
        </Link>
      </div>
    );
  }

  const unboundWorkspaces = allWorkspaces.filter(
    (ws) => !boundWorkspaces.some((bws) => bws.id === ws.id)
  );

  const tabs = [
    { key: "overview" as Tab, label: t("tabOverview"), icon: Settings },
    { key: "members" as Tab, label: t("tabMembers"), icon: Users },
    { key: "workspaces" as Tab, label: t("tabWorkspaces"), icon: HardDrive },
  ];

  return (
    <div>
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-3 mb-4">
          <Link href="/orgs">
            <Button variant="ghost" size="sm">
              <ArrowLeft size={14} />
            </Button>
          </Link>
          <div className="flex-1">
            <h1 className="text-2xl font-bold text-foreground">{org.name}</h1>
            <p className="text-sm text-muted-foreground font-mono">/{org.slug}</p>
          </div>
          <Badge color={org.status === "ACTIVE" ? "green" : "gray"}>
            {org.status.toLowerCase()}
          </Badge>
          {canInvite && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setShowInvite(true)}
            >
              <UserPlus size={13} />
              Invite Member
            </Button>
          )}
        </div>

        {/* Sub-navigation links */}
        <div className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground mb-6">
          <Link
            href={`/orgs/${id}/model-config`}
            className="flex items-center gap-1.5 hover:text-primary transition-colors"
          >
            <Key size={14} />
            {t("navModelConfig")}
          </Link>
          <Link
            href={`/orgs/${id}/db-connections`}
            className="flex items-center gap-1.5 hover:text-primary transition-colors"
          >
            <Database size={14} />
            {t("navDbConnections")}
          </Link>
          <Link
            href={`/orgs/${id}/build-env`}
            className="flex items-center gap-1.5 hover:text-primary transition-colors"
          >
            <Settings size={14} />
            {t("navBuildEnv")}
          </Link>
          <Link
            href={`/orgs/${id}/usage`}
            className="flex items-center gap-1.5 hover:text-primary transition-colors"
          >
            <BarChart2 size={14} />
            Usage
          </Link>
          <Link
            href={`/orgs/${id}/audit-log`}
            className="flex items-center gap-1.5 hover:text-primary transition-colors"
          >
            <ClipboardList size={14} />
            Audit Log
          </Link>
          <Link
            href={`/orgs/${id}/governance`}
            className="flex items-center gap-1.5 hover:text-primary transition-colors"
          >
            <ShieldCheck size={14} />
            Governance
          </Link>
          <Link
            href={`/orgs/${id}/eval`}
            className="flex items-center gap-1.5 hover:text-primary transition-colors"
          >
            <FlaskConical size={14} />
            Eval
          </Link>
          {isAdmin && (
            <Link
              href={`/orgs/${id}/quota`}
              className="flex items-center gap-1.5 hover:text-primary transition-colors"
            >
              <Gauge size={14} />
              Quota
            </Link>
          )}
        </div>

        {/* Tabs */}
        <div className="flex gap-1 border-b border-border">
          {tabs.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                tab === key
                  ? "border-primary text-primary"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              }`}
            >
              <Icon size={14} />
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Overview tab */}
      {tab === "overview" && (
        <div className="space-y-4 max-w-lg">
          {editing ? (
            <Card title={t("editTitle")}>
              <div className="space-y-3">
                <Input
                  label={t("fieldName")}
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                />
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-medium text-muted-foreground">
                    {t("fieldDesc")}
                  </label>
                  <textarea
                    className="rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none focus:ring-1 focus:ring-ring resize-none"
                    rows={3}
                    value={editDesc}
                    onChange={(e) => setEditDesc(e.target.value)}
                  />
                </div>
                <div className="flex gap-2 pt-1">
                  <Button
                    loading={updateMutation.isPending}
                    onClick={() =>
                      updateMutation.mutate({
                        name: editName,
                        description: editDesc,
                      })
                    }
                  >
                    {t("saveBtn")}
                  </Button>
                  <Button variant="secondary" onClick={() => setEditing(false)}>
                    {t("cancel")}
                  </Button>
                </div>
              </div>
            </Card>
          ) : (
            <Card title={t("detailsTitle")}>
              <dl className="space-y-3">
                <div>
                  <dt className="text-xs text-muted-foreground">{t("fieldName")}</dt>
                  <dd className="mt-0.5 text-sm text-foreground">{org.name}</dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">{t("fieldSlug")}</dt>
                  <dd className="mt-0.5 text-sm font-mono text-foreground">
                    {org.slug}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">{t("fieldDesc")}</dt>
                  <dd className="mt-0.5 text-sm text-foreground">
                    {org.description || (
                      <span className="text-muted-foreground">{t("noDesc")}</span>
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">{t("fieldStatus")}</dt>
                  <dd className="mt-0.5">
                    <Badge color={org.status === "ACTIVE" ? "green" : "gray"}>
                      {org.status.toLowerCase()}
                    </Badge>
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-muted-foreground">{t("fieldCreated")}</dt>
                  <dd className="mt-0.5 text-sm text-foreground">
                    {new Date(org.createdAt).toLocaleString()}
                  </dd>
                </div>
              </dl>
              <div className="mt-4 flex gap-2 border-t border-border pt-4">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setEditName(org.name);
                    setEditDesc(org.description || "");
                    setEditing(true);
                  }}
                >
                  {t("editBtn")}
                </Button>
                {isAdmin && (
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => {
                      if (confirm(t("deleteConfirm", { name: org.name }))) {
                        deleteMutation.mutate();
                      }
                    }}
                    loading={deleteMutation.isPending}
                  >
                    <Trash2 size={13} />
                    {t("deleteBtn")}
                  </Button>
                )}
              </div>
            </Card>
          )}
        </div>
      )}

      {/* Members tab */}
      {tab === "members" && (
        <div className="space-y-4">
          {canInvite && (
            <Card title={t("addMemberTitle")}>
              {mutationError && (
                <div className="mb-3 rounded-md border border-destructive/40 bg-destructive/20 px-3 py-2 text-sm text-destructive-foreground">
                  {mutationError}
                </div>
              )}
              <div className="flex gap-2">
                <Input
                  placeholder={t("userIdPlaceholder")}
                  value={newUserId}
                  onChange={(e) => setNewUserId(e.target.value)}
                  className="flex-1"
                />
                <select
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
                  value={newRole}
                  onChange={(e) => setNewRole(e.target.value)}
                >
                  <option value="MEMBER">{t("roleMember")}</option>
                  <option value="ADMIN">{t("roleAdmin")}</option>
                  <option value="OWNER">{t("roleOwner")}</option>
                </select>
                <Button
                  onClick={() => addMemberMutation.mutate()}
                  disabled={!newUserId.trim()}
                  loading={addMemberMutation.isPending}
                >
                  <Plus size={14} />
                  {t("addBtn")}
                </Button>
              </div>
            </Card>
          )}

          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-sm">
              <thead className="border-b border-border bg-muted/50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colUserId")}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colRole")}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colJoined")}</th>
                  {canInvite && (
                    <th className="px-4 py-3 text-right text-xs font-medium uppercase text-muted-foreground">{t("colActions")}</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {members.length === 0 ? (
                  <tr>
                    <td colSpan={canInvite ? 4 : 3} className="px-4 py-8 text-center text-muted-foreground">
                      {t("noMembers")}
                    </td>
                  </tr>
                ) : (
                  members.map((m) => (
                    <tr key={m.userId} className="hover:bg-accent/30 transition-colors">
                      <td className="px-4 py-3 font-mono text-xs text-foreground">{m.userId}</td>
                      <td className="px-4 py-3">
                        <Badge color={m.role === "OWNER" ? "blue" : m.role === "ADMIN" ? "yellow" : "gray"}>
                          {m.role.toLowerCase()}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {new Date(m.joinedAt).toLocaleDateString()}
                      </td>
                      {canInvite && (
                        <td className="px-4 py-3 text-right">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive hover:bg-destructive/10"
                            onClick={() => removeMemberMutation.mutate(m.userId)}
                          >
                            <X size={13} />
                          </Button>
                        </td>
                      )}
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Workspaces tab */}
      {tab === "workspaces" && (
        <div className="space-y-4">
          <Card title={t("bindTitle")}>
            <div className="flex gap-2">
              <select
                className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground"
                value={selectedWsId}
                onChange={(e) => setSelectedWsId(e.target.value)}
              >
                <option value="">{t("selectWs")}</option>
                {unboundWorkspaces.map((ws) => (
                  <option key={ws.id} value={ws.id}>
                    {ws.name}
                  </option>
                ))}
              </select>
              <Button
                disabled={!selectedWsId}
                loading={bindMutation.isPending}
                onClick={() => selectedWsId && bindMutation.mutate(selectedWsId)}
              >
                <LinkIcon size={14} />
                {t("bindBtn")}
              </Button>
            </div>
          </Card>

          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-sm">
              <thead className="border-b border-border bg-muted/50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colWorkspace")}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colOwner")}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colStatus")}</th>
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase text-muted-foreground">{t("colActions")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {boundWorkspaces.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground">
                      {t("noWorkspaces")}
                    </td>
                  </tr>
                ) : (
                  boundWorkspaces.map((ws) => (
                    <tr key={ws.id} className="hover:bg-accent/30 transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">{ws.name}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground font-mono">{ws.owner}</td>
                      <td className="px-4 py-3">
                        <Badge color={ws.status === "active" ? "green" : "gray"}>
                          {ws.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive hover:bg-destructive/10"
                          onClick={() => unbindMutation.mutate(ws.id)}
                        >
                          <X size={13} />
                          {t("unbindBtn")}
                        </Button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Invite Modal */}
      {showInvite && (
        <InviteModal orgId={id} onClose={() => setShowInvite(false)} />
      )}
    </div>
  );
}
