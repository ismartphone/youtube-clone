import type { TokenPair } from "@/models/user";

// Tokens de la session, conservés dans le navigateur (localStorage) pour survivre à un rechargement.
// Limite connue : un script injecté dans la page pourrait les lire. Le back-office n'affiche
// que du texte échappé par React, et l'access token expire au bout de 15 minutes (ADR-008).

const STORAGE_KEY = "backoffice.session";

export function readTokens(): TokenPair | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<TokenPair>;
    return parsed.accessToken && parsed.refreshToken
      ? { accessToken: parsed.accessToken, refreshToken: parsed.refreshToken }
      : null;
  } catch {
    return null;
  }
}

export function saveTokens(tokens: TokenPair): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
}

export function clearTokens(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(STORAGE_KEY);
}
