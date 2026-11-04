"use client";

import { useState } from "react";
import {
  AD_ADVERTISER_MAX_LENGTH,
  AD_CTA_MAX_LENGTH,
  AD_DESCRIPTION_MAX_LENGTH,
  AD_TITLE_MAX_LENGTH,
  MAX_AD_FILE_BYTES,
  MAX_AD_LOGO_BYTES,
  type Ad,
} from "@/models/ads";
import { deleteAd, importAd, listAds, removeAdLogo, setAdLogo, updateAd } from "@/services/adsService";
import { errorMessage, useRemoteData } from "./useRemoteData";

/** Champs de la carte du bandeau sponsorisé, tels que saisis. */
export interface AdDraft {
  title: string;
  destinationUrl: string;
  advertiserName: string;
  description: string;
  // Vide : « Visiter le site ».
  ctaLabel: string;
}

const EMPTY_DRAFT: AdDraft = { title: "", destinationUrl: "", advertiserName: "", description: "", ctaLabel: "" };

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
  const advertiser = draft.advertiserName.trim();
  if (!advertiser) return "Saisissez le nom de l'annonceur.";
  if (advertiser.length > AD_ADVERTISER_MAX_LENGTH) {
    return `Le nom de l'annonceur ne doit pas dépasser ${AD_ADVERTISER_MAX_LENGTH} caractères.`;
  }
  const description = draft.description.trim();
  if (!description) return "Saisissez une description courte.";
  if (description.length > AD_DESCRIPTION_MAX_LENGTH) {
    return `La description ne doit pas dépasser ${AD_DESCRIPTION_MAX_LENGTH} caractères.`;
  }
  if (draft.ctaLabel.trim().length > AD_CTA_MAX_LENGTH) {
    return `Le libellé du bouton ne doit pas dépasser ${AD_CTA_MAX_LENGTH} caractères.`;
  }
  return null;
}

// Libellé vide : le serveur garde « Visiter le site ».
function fieldsOf(draft: AdDraft) {
  return {
    title: draft.title.trim(),
    destinationUrl: draft.destinationUrl.trim(),
    advertiserName: draft.advertiserName.trim(),
    description: draft.description.trim(),
    ...(draft.ctaLabel.trim() ? { ctaLabel: draft.ctaLabel.trim() } : {}),
  };
}

function logoError(logo: File | null): string | null {
  return logo && logo.size > MAX_AD_LOGO_BYTES ? "Le logo dépasse 5 Mo." : null;
}

export function useAdsController() {
  const { state, reload } = useRemoteData("ads", listAds);
  const [draft, setDraft] = useState<AdDraft>(EMPTY_DRAFT);
  const [file, setFile] = useState<File | null>(null);
  const [logo, setLogo] = useState<File | null>(null);
  // Remis à zéro après un import, pour vider les sélecteurs de fichier.
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
    return (
      rowDrafts[ad.id] ?? {
        title: ad.title,
        destinationUrl: ad.destinationUrl ?? "",
        advertiserName: ad.advertiserName ?? "",
        description: ad.description ?? "",
        ctaLabel: ad.ctaLabel,
      }
    );
  }

  function changeRow(ad: Ad, changes: Partial<AdDraft>) {
    setRowDrafts((current) => ({ ...current, [ad.id]: { ...rowDraft(ad), ...changes } }));
    setRowErrors((current) => without(current, ad.id));
  }

  function isRowChanged(ad: Ad): boolean {
    const current = rowDraft(ad);
    return (
      current.title.trim() !== ad.title ||
      current.destinationUrl.trim() !== (ad.destinationUrl ?? "") ||
      current.advertiserName.trim() !== (ad.advertiserName ?? "") ||
      current.description.trim() !== (ad.description ?? "") ||
      current.ctaLabel.trim() !== ad.ctaLabel
    );
  }

  async function saveRow(ad: Ad) {
    const current = rowDraft(ad);
    const error = draftError(current);
    if (error) return setRowErrors((errors) => ({ ...errors, [ad.id]: error }));
    setSavingId(ad.id);
    try {
      await updateAd(ad.id, fieldsOf(current));
      setRowDrafts((drafts) => without(drafts, ad.id));
      reload();
    } catch (failure) {
      setRowErrors((errors) => ({ ...errors, [ad.id]: errorMessage(failure) }));
    } finally {
      setSavingId(null);
    }
  }

  // Logo d'une publicité déjà importée : envoyé dès qu'il est choisi.
  async function changeRowLogo(ad: Ad, value: File | null) {
    if (!value) return;
    const error = logoError(value);
    if (error) return setRowErrors((errors) => ({ ...errors, [ad.id]: error }));
    setSavingId(ad.id);
    try {
      await setAdLogo(ad.id, value);
      reload();
    } catch (failure) {
      setRowErrors((errors) => ({ ...errors, [ad.id]: errorMessage(failure) }));
    } finally {
      setSavingId(null);
    }
  }

  async function removeRowLogo(ad: Ad) {
    setSavingId(ad.id);
    try {
      await removeAdLogo(ad.id);
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

  function changeLogo(value: File | null) {
    setLogo(value);
    setFormError(null);
  }

  async function submit() {
    const error = draftError(draft) ?? logoError(logo);
    if (error) return setFormError(error);
    if (!file) return setFormError("Choisissez un fichier vidéo.");
    if (file.size > MAX_AD_FILE_BYTES) return setFormError("Le fichier dépasse 200 Mo.");

    setImporting(true);
    try {
      const ad = await importAd(fieldsOf(draft), file);
      // Le logo part une fois la publicité créée ; refusé, la publicité reste, sans logo.
      if (logo) {
        try {
          await setAdLogo(ad.id, logo);
        } catch (failure) {
          setActionError(`Publicité importée, mais logo refusé : ${errorMessage(failure)}`);
        }
      }
      setDraft(EMPTY_DRAFT);
      setFile(null);
      setLogo(null);
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
    changeLogo,
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
    changeRowLogo,
    removeRowLogo,
  };
}

function without<T>(record: Record<string, T>, key: string): Record<string, T> {
  const copy = { ...record };
  delete copy[key];
  return copy;
}
