"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/controllers/session";
import { AdminShell } from "@/views/AdminShell";
import { ErrorBanner, Loading } from "@/views/ui";

// Garde de toutes les pages d'administration : sans session administrateur, retour à /login.
// L'API vérifie de son côté le rôle ADMIN sur chaque route : cette garde ne sert qu'à l'affichage.
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { session, logout, retry } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (session.status === "anonymous") router.replace("/login");
  }, [session.status, router]);

  if (session.status === "error") {
    return (
      <main className="mx-auto max-w-xl px-4 py-16">
        <ErrorBanner message={session.message} onRetry={retry} />
      </main>
    );
  }
  if (session.status !== "authenticated") {
    return <Loading />;
  }
  return (
    <AdminShell userName={session.user.displayName} onLogout={logout}>
      {children}
    </AdminShell>
  );
}
