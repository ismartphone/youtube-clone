"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/services/apiClient";

export type RemoteData<T> =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; data: T };

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "Une erreur inattendue est survenue.";
}

/**
 * Charge une donnée de l'API. Un changement de [key] (par exemple le numéro de page)
 * relance le chargement ; reload() le relance à la demande, après une modification.
 */
export function useRemoteData<T>(key: string, fetcher: () => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ requestKey: string; value: RemoteData<T> } | null>(null);

  // Toujours la dernière version du chargeur, sans relancer l'effet à chaque rendu.
  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const requestKey = `${key}#${attempt}`;
  useEffect(() => {
    let active = true;
    fetcherRef.current().then(
      (data) => active && setResult({ requestKey, value: { status: "success", data } }),
      (error: unknown) =>
        active && setResult({ requestKey, value: { status: "error", message: errorMessage(error) } }),
    );
    // Réponse d'une requête dépassée (page changée entre-temps) : ignorée.
    return () => {
      active = false;
    };
  }, [requestKey]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);
  const state: RemoteData<T> =
    result?.requestKey === requestKey ? result.value : { status: "loading" };
  return { state, reload };
}
