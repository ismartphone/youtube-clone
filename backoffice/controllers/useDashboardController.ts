"use client";

import { mostViewed } from "@/models/catalog";
import { getStats, listAllVideos } from "@/services/catalogService";
import { useRemoteData } from "./useRemoteData";

const TOP_VIDEOS_COUNT = 10;

// Les statistiques et le classement se chargent séparément : l'échec de l'un n'empêche pas
// l'affichage de l'autre.
export function useDashboardController() {
  const stats = useRemoteData("stats", getStats);
  const topVideos = useRemoteData("top-videos", async () => mostViewed(await listAllVideos(), TOP_VIDEOS_COUNT));
  return { stats, topVideos };
}
