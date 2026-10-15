"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { isAdmin, type User } from "@/models/user";
import { ApiError, onSessionExpired } from "@/services/apiClient";
import * as authService from "@/services/authService";

export type SessionState =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "error"; message: string }
  | { status: "authenticated"; user: User };

interface SessionContextValue {
  session: SessionState;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  retry: () => void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

/** Session de l'administrateur, partagée par toutes les pages du back-office. */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<SessionState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  // Au chargement : reprend la session enregistrée si elle est encore valable.
  useEffect(() => {
    let active = true;
    authService.getSessionUser().then(
      (user) => {
        if (!active) return;
        if (user && isAdmin(user)) {
          setSession({ status: "authenticated", user });
        } else {
          // Pas de session, ou rôle retiré depuis la dernière connexion.
          authService.logout();
          setSession({ status: "anonymous" });
        }
      },
      (error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.status !== 0 && error.status < 500) {
          authService.logout();
          setSession({ status: "anonymous" });
        } else {
          // API injoignable ou en erreur : on garde les tokens et on propose de réessayer.
          setSession({
            status: "error",
            message: error instanceof ApiError ? error.message : "Erreur inattendue.",
          });
        }
      },
    );
    return () => {
      active = false;
    };
  }, [attempt]);

  // Refresh token refusé pendant l'utilisation : retour à l'écran de connexion.
  useEffect(() => onSessionExpired(() => setSession({ status: "anonymous" })), []);

  const login = useCallback(async (email: string, password: string) => {
    const user = await authService.login(email, password);
    setSession({ status: "authenticated", user });
  }, []);

  const logout = useCallback(() => {
    authService.logout();
    setSession({ status: "anonymous" });
  }, []);

  const retry = useCallback(() => {
    setSession({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  const value = useMemo(() => ({ session, login, logout, retry }), [session, login, logout, retry]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) throw new Error("useSession doit être utilisé dans un SessionProvider");
  return context;
}
