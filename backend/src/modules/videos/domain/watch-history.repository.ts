import type { Video } from './video.js';
import type { Page, Pagination } from './video.repository.js';

// Historique de visionnage (WATCH_HISTORY) d'un utilisateur connecté.

export const WATCH_HISTORY_REPOSITORY = Symbol('WATCH_HISTORY_REPOSITORY');

export interface WatchHistoryRepository {
  // Une ligne par couple utilisateur et vidéo : revoir une vidéo la remet en tête de l'historique.
  record(userId: string, videoId: string): Promise<void>;
  // Vidéos regardées, de la plus récemment vue à la plus ancienne.
  findPage(userId: string, pagination: Pagination): Promise<Page<Video>>;
}
