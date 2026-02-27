"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
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
} from "lucide-react";
import Link from "next/link";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";

type Tab = "overview" | "members" | "workspaces";

export default function OrgDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>("overview");

  // Data queries
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

  // Edit state
  const [editName, setEditName] = useState("");
  const [editDesc, setEditDesc] = useState("");
  const [editing, setEditing] = useState(false);

  // Add member state
  const [newUserId, setNewUserId] = useState("");
  const [newRole, setNewRole] = useState("MEMBER");
  const [mutationError, setMutationError] = useState<string | null>(null);

  // Bind workspace
  const [selectedWsId, setSelectedWsId] = useState("");

  // Mutations
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
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
      </div>
    );
  }

  if (!org) {
    return (
      <div className="text-center py-20">
        <p className="text-gray-400">Organization not found</p>
        <Link href="/orgs" className="mt-4 inline-block">
          <Button variant="secondary">Back to Organizations</Button>
        </Link>
      </div>
    );
  }

  const unboundWorkspaces = allWorkspaces.filter(
    (ws) => !boundWorkspaces.some((bws) => bws.id === ws.id)
  );

  const tabs = [
    { key: "overview" as Tab, label: "Overview", icon: Settings },
    { key: "members" as Tab, label: "Members", icon: Users },
    { key: "workspaces" as Tab, label: "Workspaces", icon: HardDrive },
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
            <h1 className="text-2xl font-bold text-white">{org.name}</h1>
            <p className="text-sm text-gray-400 font-mono">/{org.slug}</p>
          </div>
          <Badge color={org.status === "ACTIVE" ? "green" : "gray"}>
            {org.status.toLowerCase()}
          </Badge>
        </div>

        {/* Sub-navigation links */}
        <div className="flex items-center gap-4 text-sm text-gray-400 mb-6">
          <Link
            href={`/orgs/${id}/model-config`}
            className="flex items-center gap-1.5 hover:text-indigo-400 transition-colors"
          >
            <Key size={14} />
            Model Config
          </Link>
          <Link
            href={`/orgs/${id}/db-connections`}
            className="flex items-center gap-1.5 hover:text-indigo-400 transition-colors"
          >
            <Database size={14} />
            DB Connections
          </Link>
          <Link
            href={`/orgs/${id}/build-env`}
            className="flex items-center gap-1.5 hover:text-indigo-400 transition-colors"
          >
            <Settings size={14} />
            Build Env
          </Link>
        </div>

        {/* Tabs */}
        <div className="flex gap-1 border-b border-gray-700">
          {tabs.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                tab === key
                  ? "border-indigo-500 text-indigo-300"
                  : "border-transparent text-gray-400 hover:text-gray-200"
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
            <Card title="Edit Organization">
              <div className="space-y-3">
                <Input
                  label="Name"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                />
                <div className="flex flex-col gap-1">
                  <label className="text-xs font-medium text-gray-400">
                    Description
                  </label>
                  <textarea
                    className="rounded-md border border-gray-600 bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 resize-none"
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
                    Save Changes
                  </Button>
                  <Button variant="secondary" onClick={() => setEditing(false)}>
                    Cancel
                  </Button>
                </div>
              </div>
            </Card>
          ) : (
            <Card title="Organization Details">
              <dl className="space-y-3">
                <div>
                  <dt className="text-xs text-gray-400">Name</dt>
                  <dd className="mt-0.5 text-sm text-gray-200">{org.name}</dd>
                </div>
                <div>
                  <dt className="text-xs text-gray-400">Slug</dt>
                  <dd className="mt-0.5 text-sm font-mono text-gray-200">
                    {org.slug}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-gray-400">Description</dt>
                  <dd className="mt-0.5 text-sm text-gray-200">
                    {org.description || (
                      <span className="text-gray-500">—</span>
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-gray-400">Status</dt>
                  <dd className="mt-0.5">
                    <Badge color={org.status === "ACTIVE" ? "green" : "gray"}>
                      {org.status.toLowerCase()}
                    </Badge>
                  </dd>
                </div>
                <div>
                  <dt className="text-xs text-gray-400">Created</dt>
                  <dd className="mt-0.5 text-sm text-gray-200">
                    {new Date(org.createdAt).toLocaleString()}
                  </dd>
                </div>
              </dl>
              <div className="mt-4 flex gap-2 border-t border-gray-700 pt-4">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setEditName(org.name);
                    setEditDesc(org.description || "");
                    setEditing(true);
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => {
                    if (
                      confirm(
                        `Delete "${org.name}"? This will delete all associated data.`
                      )
                    ) {
                      deleteMutation.mutate();
                    }
                  }}
                  loading={deleteMutation.isPending}
                >
                  <Trash2 size={13} />
                  Delete Org
                </Button>
              </div>
            </Card>
          )}
        </div>
      )}

      {/* Members tab */}
      {tab === "members" && (
        <div className="space-y-4">
          <Card title="Add Member">
            {mutationError && (
              <div className="mb-3 rounded-md border border-red-800 bg-red-900/30 px-3 py-2 text-sm text-red-300">
                {mutationError}
              </div>
            )}
            <div className="flex gap-2">
              <Input
                placeholder="User ID"
                value={newUserId}
                onChange={(e) => setNewUserId(e.target.value)}
                className="flex-1"
              />
              <select
                className="rounded-md border border-gray-600 bg-gray-800 px-3 py-2 text-sm text-gray-100"
                value={newRole}
                onChange={(e) => setNewRole(e.target.value)}
              >
                <option value="MEMBER">Member</option>
                <option value="ADMIN">Admin</option>
                <option value="OWNER">Owner</option>
              </select>
              <Button
                onClick={() => addMemberMutation.mutate()}
                disabled={!newUserId.trim()}
                loading={addMemberMutation.isPending}
              >
                <Plus size={14} />
                Add
              </Button>
            </div>
          </Card>

          <div className="overflow-x-auto rounded-lg border border-gray-700">
            <table className="w-full text-sm">
              <thead className="border-b border-gray-700 bg-gray-900/50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-400">
                    User ID
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-400">
                    Role
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-400">
                    Joined
                  </th>
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase text-gray-400">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700/50">
                {members.length === 0 ? (
                  <tr>
                    <td
                      colSpan={4}
                      className="px-4 py-8 text-center text-gray-500"
                    >
                      No members yet
                    </td>
                  </tr>
                ) : (
                  members.map((m) => (
                    <tr
                      key={m.userId}
                      className="hover:bg-gray-800/30 transition-colors"
                    >
                      <td className="px-4 py-3 font-mono text-xs text-gray-300">
                        {m.userId}
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          color={
                            m.role === "OWNER"
                              ? "blue"
                              : m.role === "ADMIN"
                              ? "yellow"
                              : "gray"
                          }
                        >
                          {m.role.toLowerCase()}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        {new Date(m.joinedAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-red-400 hover:text-red-300"
                          onClick={() =>
                            removeMemberMutation.mutate(m.userId)
                          }
                        >
                          <X size={13} />
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

      {/* Workspaces tab */}
      {tab === "workspaces" && (
        <div className="space-y-4">
          <Card title="Bind Workspace">
            <div className="flex gap-2">
              <select
                className="flex-1 rounded-md border border-gray-600 bg-gray-800 px-3 py-2 text-sm text-gray-100"
                value={selectedWsId}
                onChange={(e) => setSelectedWsId(e.target.value)}
              >
                <option value="">Select workspace to bind...</option>
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
                Bind
              </Button>
            </div>
          </Card>

          <div className="overflow-x-auto rounded-lg border border-gray-700">
            <table className="w-full text-sm">
              <thead className="border-b border-gray-700 bg-gray-900/50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-400">
                    Workspace
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-400">
                    Owner
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase text-gray-400">
                    Status
                  </th>
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase text-gray-400">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700/50">
                {boundWorkspaces.length === 0 ? (
                  <tr>
                    <td
                      colSpan={4}
                      className="px-4 py-8 text-center text-gray-500"
                    >
                      No workspaces bound to this organization
                    </td>
                  </tr>
                ) : (
                  boundWorkspaces.map((ws) => (
                    <tr
                      key={ws.id}
                      className="hover:bg-gray-800/30 transition-colors"
                    >
                      <td className="px-4 py-3 font-medium text-gray-200">
                        {ws.name}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500 font-mono">
                        {ws.owner}
                      </td>
                      <td className="px-4 py-3">
                        <Badge
                          color={ws.status === "active" ? "green" : "gray"}
                        >
                          {ws.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-red-400 hover:text-red-300"
                          onClick={() => unbindMutation.mutate(ws.id)}
                        >
                          <X size={13} />
                          Unbind
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
    </div>
  );
}
