"use client";

import { useState } from "react";
import type { Video } from "@/models/catalog";
import { deleteVideo, listVideos } from "@/services/catalogService";
import { errorMessage, useRemoteData } from "./useRemoteData";

const PAGE_SIZE = 20;

export function useVideoListController() {
  const [page, setPage] = useState(1);
  const { state, reload } = useRemoteData(`videos:${page}`, () => listVideos(page, PAGE_SIZE));
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function remove(video: Video) {
    if (!window.confirm(`Supprimer « ${video.title} » ? Ses commentaires et « j'aime » seront aussi supprimés.`)) {
      return;
    }
    setDeletingId(video.id);
    setActionError(null);
    try {
      await deleteVideo(video.id);
      // Dernière vidéo d'une page qui n'est pas la première : on revient à la page précédente.
      const lastOnPage = state.status === "success" && state.data.items.length === 1;
      if (lastOnPage && page > 1) setPage(page - 1);
      else reload();
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setDeletingId(null);
    }
  }

  return { state, reload, page, setPage, deletingId, actionError, remove };
}
