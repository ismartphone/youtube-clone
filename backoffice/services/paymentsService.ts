import type { Page } from "@/models/catalog";
import type { RevenueStats, Subscription } from "@/models/payments";
import { apiRequest } from "./apiClient";

// Section Paiements : routes /admin/premium de l'API, réservées au rôle ADMIN (AdminGuard côté
// backend). Elles renvoient des données personnelles et financières.

export function listSubscriptions(page: number, pageSize: number): Promise<Page<Subscription>> {
  return apiRequest(`/admin/premium/subscriptions?page=${page}&pageSize=${pageSize}`);
}

export function getRevenueStats(): Promise<RevenueStats> {
  return apiRequest("/admin/premium/stats");
}
