import type { TokenPair } from "@/models/user";
import { clearTokens, readTokens, saveTokens } from "./tokenStorage";

// Client HTTP du back-office. Toutes les requêtes passent par /api, relayé vers l'API NestJS
// (voir next.config.ts). Ajoute l'access token, et le renouvelle automatiquement sur une 401.

const API_PREFIX = "/api";

/** Erreur renvoyée par l'API, avec le message destiné à l'utilisateur quand il existe. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  // Token à utiliser à la place de la session enregistrée (vérification du rôle à la connexion).
  accessToken?: string;
}

// Prévenu quand le renouvellement échoue : la session est terminée, il faut se reconnecter.
const sessionExpiredListeners = new Set<() => void>();

export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = options.accessToken ?? readTokens()?.accessToken;
  let response = await send(path, options, token);

  // Access token expiré : on le renouvelle une fois, puis on rejoue la requête.
  if (response.status === 401 && token && !options.accessToken) {
    const renewed = await refreshTokens();
    if (renewed) {
      response = await send(path, options, renewed.accessToken);
    }
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function send(path: string, options: RequestOptions, token: string | undefined) {
  const headers: Record<string, string> = {};
  // Un envoi de fichier (FormData) garde l'en-tête multipart choisi par le navigateur.
  const isForm = options.body instanceof FormData;
  if (options.body !== undefined && !isForm) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(API_PREFIX + path, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
  }).catch(() => {
    throw new ApiError(0, "Impossible de joindre l'API. Vérifiez que le backend est démarré.");
  });
}

// L'API n'accepte chaque refresh token qu'une fois : les requêtes qui reçoivent une 401
// en même temps partagent le même renouvellement.
let pendingRefresh: Promise<TokenPair | null> | null = null;

function refreshTokens(): Promise<TokenPair | null> {
  pendingRefresh ??= doRefresh().finally(() => {
    pendingRefresh = null;
  });
  return pendingRefresh;
}

async function doRefresh(): Promise<TokenPair | null> {
  const current = readTokens();
  if (!current) return null;
  let response: Response;
  try {
    response = await fetch(`${API_PREFIX}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: current.refreshToken }),
    });
  } catch {
    // API injoignable : la session est conservée, la requête échoue normalement.
    return null;
  }
  if (!response.ok) {
    // Refresh token expiré, déjà utilisé ou invalide : la session est terminée.
    if (response.status === 400 || response.status === 401) {
      clearTokens();
      sessionExpiredListeners.forEach((listener) => listener());
    }
    return null;
  }
  const { accessToken, refreshToken } = (await response.json()) as TokenPair;
  const renewed = { accessToken, refreshToken };
  saveTokens(renewed);
  return renewed;
}

// Corps d'erreur NestJS : { statusCode, message, error }. "message" est une liste quand
// plusieurs champs sont refusés par la validation : on les joint.
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string | string[] };
    if (Array.isArray(body.message)) return body.message.join(" ");
    if (body.message) return body.message;
  } catch {
    // Corps absent ou illisible : message générique ci-dessous.
  }
  return `L'API a renvoyé une erreur (${response.status}).`;
}
