import type { Ad, AdFields } from "@/models/ads";
import { apiRequest } from "./apiClient";

// Publicités (ADR-021), routes réservées au rôle ADMIN.

export async function listAds(): Promise<Ad[]> {
  return (await apiRequest<{ items: Ad[] }>("/admin/ads")).items;
}

// Le serveur vérifie le fichier (ffprobe), en extrait la miniature et génère son HLS.
export function importAd(fields: AdFields, file: File): Promise<Ad> {
  const form = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    if (value !== undefined) form.append(name, value);
  }
  form.append("file", file);
  return apiRequest("/admin/ads", { method: "POST", body: form });
}

// Champs de la carte du bandeau sponsorisé et site ouvert par le bouton principal.
export function updateAd(id: string, fields: AdFields): Promise<Ad> {
  return apiRequest(`/admin/ads/${encodeURIComponent(id)}`, { method: "PATCH", body: fields });
}

// Logo carré de l'annonceur : JPEG ou PNG, vérifié et recadré par le serveur.
export function setAdLogo(id: string, logo: File): Promise<Ad> {
  const form = new FormData();
  form.append("logo", logo);
  return apiRequest(`/admin/ads/${encodeURIComponent(id)}/logo`, { method: "PUT", body: form });
}

export function removeAdLogo(id: string): Promise<Ad> {
  return apiRequest(`/admin/ads/${encodeURIComponent(id)}/logo`, { method: "DELETE" });
}

export function deleteAd(id: string): Promise<void> {
  return apiRequest(`/admin/ads/${encodeURIComponent(id)}`, { method: "DELETE" });
}
