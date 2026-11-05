"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { Button } from "./ui";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Tableau de bord" },
  { href: "/videos", label: "Vidéos" },
  { href: "/categories", label: "Catégories" },
  { href: "/ads", label: "Publicités" },
  { href: "/products", label: "Produits" },
  { href: "/lives", label: "Lives" },
  { href: "/channels", label: "Chaînes" },
  { href: "/payments", label: "Paiements" },
];

/** Cadre des pages d'administration : barre latérale, administrateur connecté, déconnexion. */
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
    <div className="min-h-screen md:grid md:grid-cols-[13rem_1fr]">
      {/* Fond sur toute la hauteur de la page, contenu collé en haut au défilement. */}
      <aside className="border-b border-border bg-surface md:border-r md:border-b-0">
        <div className="md:sticky md:top-0">
        <div className="flex items-center gap-2.5 px-5 py-4">
          <Logo />
          <span className="text-sm font-semibold leading-tight">
            YouTube Clone
            <span className="block text-xs font-normal text-muted">Back-office</span>
          </span>
        </div>
        {/* usePathname suspend sur les routes dynamiques (/videos/[id]) : d'où la frontière. */}
        <Suspense fallback={<NavLinks activeHref={null} />}>
          <ActiveNavLinks />
        </Suspense>
        </div>
      </aside>
      <div className="flex min-w-0 flex-col">
        <header className="flex items-center justify-end gap-3 border-b border-border px-6 py-3 text-sm">
          <span className="flex items-center gap-2">
            <span
              aria-hidden
              className="flex size-7 items-center justify-center rounded-full bg-surface-hover text-xs font-semibold"
            >
              {userName.trim().charAt(0).toUpperCase()}
            </span>
            <span>{userName}</span>
          </span>
          <Button variant="secondary" size="sm" onClick={onLogout}>
            Se déconnecter
          </Button>
        </header>
        <main className="w-full max-w-6xl px-6 py-8">{children}</main>
      </div>
    </div>
  );
}

/** Logo de l'application : rectangle rouge arrondi et triangle de lecture blanc. */
export function Logo() {
  return (
    <svg aria-hidden viewBox="0 0 28 20" className="h-5 w-7 shrink-0">
      <rect width="28" height="20" rx="5" fill="var(--accent)" />
      <path d="M11 6v8l7-4z" fill="#fff" />
    </svg>
  );
}

function ActiveNavLinks() {
  const pathname = usePathname();
  const active = NAV_ITEMS.find((item) => pathname.startsWith(item.href));
  return <NavLinks activeHref={active?.href ?? null} />;
}

function NavLinks({ activeHref }: { activeHref: string | null }) {
  return (
    <nav aria-label="Navigation principale" className="px-3 pb-3 md:pb-0">
      <ul className="flex gap-1 overflow-x-auto md:flex-col">
        {NAV_ITEMS.map((item) => (
          <li key={item.href}>
            <Link
              href={item.href}
              aria-current={item.href === activeHref ? "page" : undefined}
              className="block whitespace-nowrap rounded-lg border-l-2 border-transparent px-3 py-2 text-sm text-muted transition-colors hover:bg-surface-hover hover:text-foreground aria-[current=page]:border-accent aria-[current=page]:bg-surface-hover aria-[current=page]:font-medium aria-[current=page]:text-foreground"
            >
              {item.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
