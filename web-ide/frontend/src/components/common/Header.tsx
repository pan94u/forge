"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import {
  Anvil,
  Bell,
  Settings,
  User,
  LogOut,
  ChevronDown,
  Search,
} from "lucide-react";
import { RoleSwitch } from "./RoleSwitch";
import { logout, isAuthenticated } from "@/lib/auth";

interface HeaderProps {
  role: "developer" | "product";
  onRoleChange: (role: "developer" | "product") => void;
}

export function Header({ role, onRoleChange }: HeaderProps) {
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        setSearchOpen((prev) => !prev);
      }
      if (e.key === "Escape" && searchOpen) {
        setSearchOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [searchOpen]);

  return (
    <header className="flex h-12 items-center justify-between border-b border-border bg-card px-4">
      {/* Left - Branding */}
      <div className="flex items-center gap-6">
        <Link href="/" className="flex items-center gap-2">
          <Anvil className="h-6 w-6 text-primary" />
          <span className="text-lg font-bold tracking-tight">Forge</span>
        </Link>

        {/* Global Search */}
        <div className="hidden md:block">
          <button
            onClick={() => setSearchOpen(true)}
            className="flex items-center gap-2 rounded-md border border-input bg-background px-3 py-1 text-sm text-muted-foreground hover:bg-accent"
          >
            <Search className="h-3.5 w-3.5" />
            <span>Search...</span>
            <kbd className="ml-4 rounded border border-border bg-muted px-1.5 py-0.5 text-xs font-mono">
              Cmd+K
            </kbd>
          </button>
        </div>
      </div>

      {/* Right - Controls */}
      <div className="flex items-center gap-3">
        {/* Role Switch */}
        <RoleSwitch role={role} onRoleChange={onRoleChange} />

        {/* Notifications */}
        <button className="relative rounded-md p-1.5 text-muted-foreground hover:bg-accent hover:text-foreground">
          <Bell className="h-4 w-4" />
          <span className="absolute -top-0.5 -right-0.5 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-primary text-[9px] font-bold text-primary-foreground">
            3
          </span>
        </button>

        {/* Settings */}
        <Link
          href="/settings"
          className="rounded-md p-1.5 text-muted-foreground hover:bg-accent hover:text-foreground"
        >
          <Settings className="h-4 w-4" />
        </Link>

        {/* User Menu */}
        <div className="relative">
          <button
            onClick={() => setUserMenuOpen(!userMenuOpen)}
            className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-accent"
          >
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
              Q
            </div>
            <span className="hidden text-sm font-medium md:block">
              Qi Zhao
            </span>
            <ChevronDown className="h-3 w-3 text-muted-foreground" />
          </button>

          {userMenuOpen && (
            <>
              <div
                className="fixed inset-0 z-40"
                onClick={() => setUserMenuOpen(false)}
              />
              <div className="absolute right-0 top-full z-50 mt-1 w-48 rounded-md border border-border bg-popover shadow-lg">
                <div className="border-b border-border px-3 py-2">
                  <p className="text-sm font-medium">Qi Zhao</p>
                  <p className="text-xs text-muted-foreground">
                    qi@forge.dev
                  </p>
                </div>
                <div className="py-1">
                  <Link
                    href="/profile"
                    className="flex items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
                    onClick={() => setUserMenuOpen(false)}
                  >
                    <User className="h-3.5 w-3.5" />
                    Profile
                  </Link>
                  <Link
                    href="/settings"
                    className="flex items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
                    onClick={() => setUserMenuOpen(false)}
                  >
                    <Settings className="h-3.5 w-3.5" />
                    Settings
                  </Link>
                  <button
                    onClick={() => { setUserMenuOpen(false); logout(); }}
                    className="flex w-full items-center gap-2 px-3 py-1.5 text-sm text-destructive hover:bg-accent"
                  >
                    <LogOut className="h-3.5 w-3.5" />
                    Sign out
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Command Palette Modal */}
      {searchOpen && (
        <>
          <div
            className="fixed inset-0 z-50 bg-black/50"
            onClick={() => setSearchOpen(false)}
          />
          <div className="fixed left-1/2 top-1/4 z-50 w-full max-w-lg -translate-x-1/2 rounded-lg border border-border bg-popover shadow-2xl">
            <div className="flex items-center gap-3 border-b border-border px-4 py-3">
              <Search className="h-4 w-4 text-muted-foreground" />
              <input
                autoFocus
                className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                placeholder="Search workspaces, files, knowledge..."
              />
              <kbd className="rounded border border-border bg-muted px-1.5 py-0.5 text-xs font-mono text-muted-foreground">
                ESC
              </kbd>
            </div>
            <div className="max-h-80 overflow-auto p-2">
              <p className="px-2 py-4 text-center text-sm text-muted-foreground">
                Start typing to search...
              </p>
            </div>
          </div>
        </>
      )}
    </header>
  );
}
