"use client";

import { useState } from "react";
import { AD_TITLE_MAX_LENGTH, MAX_AD_FILE_BYTES, type Ad } from "@/models/ads";
import { deleteAd, importAd, listAds } from "@/services/adsService";
import { errorMessage, useRemoteData } from "./useRemoteData";

export function useAdsController() {
  const { state, reload } = useRemoteData("ads", listAds);
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  // Remis à zéro après un import, pour vider le sélecteur de fichier.
  const [fileInputKey, setFileInputKey] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [importing, setImporting] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  function changeTitle(value: string) {
    setTitle(value);
    setFormError(null);
  }

  function changeFile(value: File | null) {
    setFile(value);
    setFormError(null);
  }

  async function submit() {
    const trimmed = title.trim();
    if (!trimmed) return setFormError("Saisissez un titre.");
    if (trimmed.length > AD_TITLE_MAX_LENGTH) {
      return setFormError(`Le titre ne doit pas dépasser ${AD_TITLE_MAX_LENGTH} caractères.`);
    }
    if (!file) return setFormError("Choisissez un fichier vidéo.");
    if (file.size > MAX_AD_FILE_BYTES) return setFormError("Le fichier dépasse 200 Mo.");

    setImporting(true);
    try {
      await importAd(trimmed, file);
      setTitle("");
      setFile(null);
      setFileInputKey((key) => key + 1);
      reload();
    } catch (error) {
      // Fichier refusé par ffprobe (422), trop gros (413)… : affiché sous le formulaire.
      setFormError(errorMessage(error));
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
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setDeletingId(null);
    }
  }

  return {
    state,
    reload,
    title,
    changeTitle,
    file,
    changeFile,
    fileInputKey,
    formError,
    importing,
    submit,
    deletingId,
    actionError,
    remove,
  };
}
