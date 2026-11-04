import type { Ad } from "@/models/ads";
import { apiRequest } from "./apiClient";

// Publicités (ADR-021), routes réservées au rôle ADMIN.

export async function listAds(): Promise<Ad[]> {
  return (await apiRequest<{ items: Ad[] }>("/admin/ads")).items;
}

// Le serveur vérifie le fichier (ffprobe), en extrait la miniature et génère son HLS.
export function importAd(title: string, file: File): Promise<Ad> {
  const form = new FormData();
  form.append("title", title);
  form.append("file", file);
  return apiRequest("/admin/ads", { method: "POST", body: form });
}

export function deleteAd(id: string): Promise<void> {
  return apiRequest(`/admin/ads/${encodeURIComponent(id)}`, { method: "DELETE" });
}
