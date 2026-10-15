import type {
  CatalogStats,
  Category,
  CategoryWithCount,
  ChannelSummary,
  Page,
  Video,
  VideoDetail,
} from "@/models/catalog";
import type { VideoPayload } from "@/models/videoForm";
import { apiRequest } from "./apiClient";

// Routes d'administration de l'API, réservées au rôle ADMIN (AdminGuard côté backend).

export function getStats(): Promise<CatalogStats> {
  return apiRequest("/admin/stats");
}

export function listVideos(page: number, pageSize: number): Promise<Page<Video>> {
  return apiRequest(`/admin/videos?page=${page}&pageSize=${pageSize}`);
}

export function getVideo(id: string): Promise<VideoDetail> {
  return apiRequest(`/admin/videos/${encodeURIComponent(id)}`);
}

export function createVideo(payload: VideoPayload): Promise<VideoDetail> {
  return apiRequest("/admin/videos", { method: "POST", body: payload });
}

export function updateVideo(id: string, payload: VideoPayload): Promise<VideoDetail> {
  return apiRequest(`/admin/videos/${encodeURIComponent(id)}`, { method: "PATCH", body: payload });
}

export function deleteVideo(id: string): Promise<void> {
  return apiRequest(`/admin/videos/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function listChannels(): Promise<ChannelSummary[]> {
  return apiRequest("/admin/channels");
}

export function listCategories(): Promise<CategoryWithCount[]> {
  return apiRequest("/admin/categories");
}

export function createCategory(name: string): Promise<Category> {
  return apiRequest("/admin/categories", { method: "POST", body: { name } });
}

export function deleteCategory(id: string): Promise<void> {
  return apiRequest(`/admin/categories/${encodeURIComponent(id)}`, { method: "DELETE" });
}
