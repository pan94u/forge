"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";

export default function NewOrgPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: api.orgs.create,
    onSuccess: (org) => {
      qc.invalidateQueries({ queryKey: ["orgs"] });
      router.push(`/orgs/${org.id}`);
    },
    onError: (err: Error) => {
      setError(err.message);
    },
  });

  function handleNameChange(value: string) {
    setName(value);
    if (!slug || slug === autoSlug(name)) {
      setSlug(autoSlug(value));
    }
  }

  function autoSlug(n: string) {
    return n
      .toLowerCase()
      .replace(/\s+/g, "-")
      .replace(/[^a-z0-9-]/g, "")
      .slice(0, 50);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return;
    createMutation.mutate({
      name: name.trim(),
      slug: slug.trim() || autoSlug(name),
      description: description.trim() || undefined,
    });
  }

  return (
    <div className="max-w-lg">
      <div className="mb-6 flex items-center gap-3">
        <Link href="/orgs">
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-white">New Organization</h1>
          <p className="text-sm text-gray-400">
            Create a new enterprise organization
          </p>
        </div>
      </div>

      <Card>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="Organization Name"
            placeholder="Acme Corp"
            value={name}
            onChange={(e) => handleNameChange(e.target.value)}
            required
            autoFocus
          />
          <Input
            label="Slug"
            placeholder="acme-corp"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            required
            pattern="[a-z0-9\-]+"
            title="Lowercase letters, numbers and hyphens only"
          />
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-gray-400">
              Description (optional)
            </label>
            <textarea
              className="rounded-md border border-gray-600 bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 resize-none"
              rows={3}
              placeholder="Brief description of this organization..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          {error && (
            <p className="rounded-md bg-red-900/30 border border-red-800 px-3 py-2 text-sm text-red-300">
              {error}
            </p>
          )}

          <div className="flex gap-3 pt-2">
            <Button
              type="submit"
              loading={createMutation.isPending}
              disabled={!name.trim()}
            >
              Create Organization
            </Button>
            <Link href="/orgs">
              <Button variant="secondary" type="button">
                Cancel
              </Button>
            </Link>
          </div>
        </form>
      </Card>
    </div>
  );
}
