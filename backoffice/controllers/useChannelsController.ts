"use client";

import { useState } from "react";
import type { ChannelSummary } from "@/models/catalog";
import { listChannels, updateChannelBanner } from "@/services/catalogService";
import { errorMessage, useRemoteData } from "./useRemoteData";

/** Page Chaînes : bannière de chaque chaîne, modifiable une par une. */
export function useChannelsController() {
  const { state, reload } = useRemoteData("channels", listChannels);
  // Saisies en cours, par chaîne. Sans saisie, le champ affiche la bannière enregistrée.
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [savingId, setSavingId] = useState<string | null>(null);

  function bannerDraft(channel: ChannelSummary): string {
    return drafts[channel.id] ?? channel.bannerUrl ?? "";
  }

  function changeBanner(channelId: string, value: string) {
    setDrafts((current) => ({ ...current, [channelId]: value }));
    setErrors((current) => without(current, channelId));
  }

  async function saveBanner(channel: ChannelSummary) {
    const value = bannerDraft(channel).trim();
    setSavingId(channel.id);
    try {
      // Champ vide : la bannière est effacée, l'application affiche alors un aplat.
      await updateChannelBanner(channel.id, value === "" ? null : value);
      setDrafts((current) => without(current, channel.id));
      reload();
    } catch (error) {
      // Adresse refusée par l'API (400) : affichée sous le champ.
      setErrors((current) => ({ ...current, [channel.id]: errorMessage(error) }));
    } finally {
      setSavingId(null);
    }
  }

  return { state, reload, bannerDraft, changeBanner, errors, savingId, saveBanner };
}

function without(record: Record<string, string>, key: string): Record<string, string> {
  const copy = { ...record };
  delete copy[key];
  return copy;
}
