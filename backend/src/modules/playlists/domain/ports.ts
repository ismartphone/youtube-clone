import type { PlaylistDetail, PlaylistSummary } from './playlist.js';

// Ports du module playlists : les cas d'usage n'en connaissent que ces interfaces.

export const PLAYLIST_REPOSITORY = Symbol('PLAYLIST_REPOSITORY');
export const PLAYLIST_VIDEOS = Symbol('PLAYLIST_VIDEOS');

// Toutes les méthodes prennent le propriétaire : la playlist d'un autre n'est jamais trouvée.
export interface PlaylistRepository {
  // Playlists de l'utilisateur, de la plus récemment modifiée à la plus ancienne. Avec
  // [videoId], chacune indique si elle contient cette vidéo.
  listOwned(userId: string, videoId?: string): Promise<PlaylistSummary[]>;
  countOwned(userId: string): Promise<number>;
  create(userId: string, title: string): Promise<PlaylistSummary>;
  // null si la playlist n'existe pas ou n'appartient pas à l'utilisateur.
  findDetail(id: string, userId: string): Promise<PlaylistDetail | null>;
  // Nombre de vidéos, lignes masquées comprises ; null si la playlist n'est pas à l'utilisateur.
  countItems(id: string, userId: string): Promise<number | null>;
  // Les écritures renvoient false si la playlist n'est pas à l'utilisateur.
  rename(id: string, userId: string, title: string): Promise<boolean>;
  delete(id: string, userId: string): Promise<boolean>;
  // Idempotents : ajouter deux fois, ou retirer une vidéo absente, ne change rien.
  addVideo(id: string, userId: string, videoId: string): Promise<boolean>;
  removeVideo(id: string, userId: string, videoId: string): Promise<boolean>;
}

// Lecture des vidéos depuis le module playlists (monolithe à base unique, ADR-003).
export interface PlaylistVideos {
  // Vrai si la vidéo existe et que l'utilisateur peut la voir (ADR-016).
  isAccessible(videoId: string, userId: string): Promise<boolean>;
}
