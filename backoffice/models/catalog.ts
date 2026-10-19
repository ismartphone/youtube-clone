// Catalogue tel que le renvoient les routes /admin de l'API.

export type VideoFormat = "LONG" | "SHORT";

export const VIDEO_FORMATS: { value: VideoFormat; label: string }[] = [
  { value: "LONG", label: "Vidéo" },
  { value: "SHORT", label: "Short" },
];

export interface ChannelSummary {
  id: string;
  name: string;
  handle: string;
  avatarUrl: string | null;
  bannerUrl: string | null;
}

export interface Category {
  id: string;
  name: string;
}

export interface CategoryWithCount extends Category {
  videoCount: number;
}

export interface Video {
  id: string;
  title: string;
  thumbnailUrl: string;
  durationSeconds: number;
  format: VideoFormat;
  isPremium: boolean;
  viewCount: number;
  publishedAt: string;
  channel: ChannelSummary;
  category: Category | null;
}

export interface VideoDetail extends Video {
  description: string | null;
  videoUrl: string | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface CatalogStats {
  users: number;
  channels: number;
  videos: number;
  totalViews: number;
}

export function pageCount(page: Page<unknown>): number {
  return Math.max(1, Math.ceil(page.total / page.pageSize));
}
