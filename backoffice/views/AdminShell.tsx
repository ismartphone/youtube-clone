"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { Button } from "./ui";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Tableau de bord" },
  { href: "/videos", label: "Vidéos" },
  { href: "/categories", label: "Catégories" },
];

/** Cadre des pages d'administration : navigation, administrateur connecté, déconnexion. */
export function AdminShell({
  userName,
  onLogout,
  children,
}: {
  userName: string;
  onLogout: () => void;
  children: ReactNode;
}) {
  return (
    <div className="min-h-screen">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-3">
          <div className="flex flex-wrap items-center gap-6">
            <span className="font-semibold">
              <span className="text-accent">YouTube Clone</span> · Back-office
            </span>
            {/* usePathname suspend sur les routes dynamiques (/videos/[id]) : d'où la frontière. */}
            <Suspense fallback={<NavLinks activeHref={null} />}>
              <ActiveNavLinks />
            </Suspense>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-muted">{userName}</span>
            <Button variant="secondary" onClick={onLogout}>
              Se déconnecter
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </div>
  );
}

function ActiveNavLinks() {
  const pathname = usePathname();
  const active = NAV_ITEMS.find((item) => pathname.startsWith(item.href));
  return <NavLinks activeHref={active?.href ?? null} />;
}

function NavLinks({ activeHref }: { activeHref: string | null }) {
  return (
    <nav className="flex gap-1 text-sm">
      {NAV_ITEMS.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          aria-current={item.href === activeHref ? "page" : undefined}
          className="rounded-full px-3 py-1.5 text-muted transition-colors hover:bg-surface-hover hover:text-foreground aria-[current=page]:bg-surface-hover aria-[current=page]:text-foreground"
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
