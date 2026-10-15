import { isAdmin, type TokenPair, type User } from "@/models/user";
import { ApiError, apiRequest } from "./apiClient";
import { clearTokens, readTokens, saveTokens } from "./tokenStorage";

export const NOT_ADMIN_MESSAGE = "Accès réservé aux administrateurs.";

/**
 * Connexion via POST /auth/login, puis vérification du rôle avec GET /auth/me.
 * Les tokens ne sont enregistrés que pour un administrateur.
 */
export async function login(email: string, password: string): Promise<User> {
  const tokens = await apiRequest<TokenPair>("/auth/login", {
    method: "POST",
    body: { email, password },
  });
  const user = await apiRequest<User>("/auth/me", { accessToken: tokens.accessToken });
  if (!isAdmin(user)) {
    throw new ApiError(403, NOT_ADMIN_MESSAGE);
  }
  saveTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
  return user;
}

/** Utilisateur de la session enregistrée, ou null s'il n'y en a pas. */
export async function getSessionUser(): Promise<User | null> {
  if (!readTokens()) return null;
  return apiRequest<User>("/auth/me");
}

// L'API n'a pas de route de déconnexion (ADR-008) : on oublie les tokens du navigateur.
export function logout(): void {
  clearTokens();
}
