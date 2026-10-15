import type { Channel, Video, VideoFormat } from './video.js';

export const VIDEO_REPOSITORY = Symbol('VIDEO_REPOSITORY');

export interface VideoFilter {
  format?: VideoFormat;
  categoryId?: string;
  // Ne garde que les vidéos des chaînes suivies par cet utilisateur (FOLLOW).
  followedBy?: string;
}

// Compteurs tels qu'enregistrés en base, sans les variations encore dans Redis.
export interface StoredCounts {
  viewCount: number;
  likeCount: number;
}

export interface Pagination {
  page: number;
  pageSize: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

// Port de lecture du catalogue. Les vidéos sont toujours triées de la plus récente à la plus ancienne.
export interface VideoRepository {
  findPage(filter: VideoFilter, pagination: Pagination): Promise<Page<Video>>;
  findById(id: string): Promise<Video | null>;
  // Compteurs enregistrés en base, ou null si la vidéo n'existe pas.
  findCounts(id: string): Promise<StoredCounts | null>;
  findChannelWithVideos(channelId: string): Promise<Channel | null>;
}
