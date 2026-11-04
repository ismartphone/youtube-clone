"use client";

import { useState } from "react";
import { AD_TITLE_MAX_LENGTH, MAX_AD_FILE_BYTES, type Ad } from "@/models/ads";
import { deleteAd, importAd, listAds, updateAd } from "@/services/adsService";
import { errorMessage, useRemoteData } from "./useRemoteData";

/** Titre et site de l'annonceur d'une publicité, tels que saisis. */
export interface AdDraft {
  title: string;
  destinationUrl: string;
}

// Même règle que l'API : une adresse complète, en http ou https.
const URL_PATTERN = /^https?:\/\/\S+$/;

/** Message d'erreur pour [draft], ou null s'il est valide. */
function draftError(draft: AdDraft): string | null {
  const title = draft.title.trim();
  if (!title) return "Saisissez un titre.";
  if (title.length > AD_TITLE_MAX_LENGTH) return `Le titre ne doit pas dépasser ${AD_TITLE_MAX_LENGTH} caractères.`;
  if (!URL_PATTERN.test(draft.destinationUrl.trim())) {
    return "Saisissez l'adresse complète du site, par exemple https://www.exemple.fr.";
  }
  return null;
}

export function useAdsController() {
  const { state, reload } = useRemoteData("ads", listAds);
  const [draft, setDraft] = useState<AdDraft>({ title: "", destinationUrl: "" });
  const [file, setFile] = useState<File | null>(null);
  // Remis à zéro après un import, pour vider le sélecteur de fichier.
  const [fileInputKey, setFileInputKey] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [importing, setImporting] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // Modifications en cours, par publicité. Sans saisie, les champs affichent les valeurs enregistrées.
  const [rowDrafts, setRowDrafts] = useState<Record<string, AdDraft>>({});
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({});
  const [savingId, setSavingId] = useState<string | null>(null);

  function rowDraft(ad: Ad): AdDraft {
    return rowDrafts[ad.id] ?? { title: ad.title, destinationUrl: ad.destinationUrl ?? "" };
  }

  function changeRow(ad: Ad, changes: Partial<AdDraft>) {
    setRowDrafts((current) => ({ ...current, [ad.id]: { ...rowDraft(ad), ...changes } }));
    setRowErrors((current) => without(current, ad.id));
  }

  function isRowChanged(ad: Ad): boolean {
    const current = rowDraft(ad);
    return current.title.trim() !== ad.title || current.destinationUrl.trim() !== (ad.destinationUrl ?? "");
  }

  async function saveRow(ad: Ad) {
    const current = rowDraft(ad);
    const error = draftError(current);
    if (error) return setRowErrors((errors) => ({ ...errors, [ad.id]: error }));
    setSavingId(ad.id);
    try {
      await updateAd(ad.id, { title: current.title.trim(), destinationUrl: current.destinationUrl.trim() });
      setRowDrafts((drafts) => without(drafts, ad.id));
      reload();
    } catch (failure) {
      setRowErrors((errors) => ({ ...errors, [ad.id]: errorMessage(failure) }));
    } finally {
      setSavingId(null);
    }
  }

  function changeDraft(changes: Partial<AdDraft>) {
    setDraft((current) => ({ ...current, ...changes }));
    setFormError(null);
  }

  function changeFile(value: File | null) {
    setFile(value);
    setFormError(null);
  }

  async function submit() {
    const error = draftError(draft);
    if (error) return setFormError(error);
    if (!file) return setFormError("Choisissez un fichier vidéo.");
    if (file.size > MAX_AD_FILE_BYTES) return setFormError("Le fichier dépasse 200 Mo.");

    setImporting(true);
    try {
      await importAd({ title: draft.title.trim(), destinationUrl: draft.destinationUrl.trim() }, file);
      setDraft({ title: "", destinationUrl: "" });
      setFile(null);
      setFileInputKey((key) => key + 1);
      reload();
    } catch (failure) {
      // Fichier refusé par ffprobe (422), trop gros (413)… : affiché sous le formulaire.
      setFormError(errorMessage(failure));
    } finally {
      setImporting(false);
    }
  }

  async function remove(ad: Ad) {
    if (!window.confirm(`Supprimer la publicité « ${ad.title} » ? Son fichier sera supprimé.`)) return;
    setDeletingId(ad.id);
    setActionError(null);
    try {
      await deleteAd(ad.id);
      reload();
    } catch (failure) {
      setActionError(errorMessage(failure));
    } finally {
      setDeletingId(null);
    }
  }

  return {
    state,
    reload,
    draft,
    changeDraft,
    changeFile,
    fileInputKey,
    formError,
    importing,
    submit,
    deletingId,
    actionError,
    remove,
    rowDraft,
    changeRow,
    isRowChanged,
    rowErrors,
    savingId,
    saveRow,
  };
}

function without<T>(record: Record<string, T>, key: string): Record<string, T> {
  const copy = { ...record };
  delete copy[key];
  return copy;
}
