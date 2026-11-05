import type { AdminLive } from "@/models/lives";
import { apiRequest } from "./apiClient";

// Lives (ADR-029), routes réservées au rôle ADMIN. Elles renvoient des données personnelles et
// financières (acheteurs, montants).

export async function listLives(): Promise<AdminLive[]> {
  return (await apiRequest<{ items: AdminLive[] }>("/admin/lives")).items;
}

// Débloque un live resté ouvert : ses spectateurs en sont prévenus et sortent de la salle.
export function endLive(id: string): Promise<void> {
  return apiRequest(`/admin/lives/${encodeURIComponent(id)}/end`, { method: "POST" });
}
