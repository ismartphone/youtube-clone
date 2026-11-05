"use client";

import { useState } from "react";
import type { AdminLive } from "@/models/lives";
import { endLive, listLives } from "@/services/livesService";
import { errorMessage, useRemoteData } from "./useRemoteData";

/** Page Lives : tous les lives avec leurs achats, et fin d'un live resté ouvert (ADR-029, D2). */
export function useLivesController() {
  const { state, reload } = useRemoteData("lives", listLives);
  const [endingId, setEndingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function end(live: AdminLive) {
    if (!window.confirm(`Terminer le live « ${live.title} » ? Ses spectateurs sont prévenus et quittent la salle.`)) {
      return;
    }
    setEndingId(live.id);
    setActionError(null);
    try {
      await endLive(live.id);
      reload();
    } catch (failure) {
      setActionError(errorMessage(failure));
    } finally {
      setEndingId(null);
    }
  }

  return { state, reload, endingId, actionError, end };
}
