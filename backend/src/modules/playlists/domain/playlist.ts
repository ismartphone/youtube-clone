import type { Video } from '../../videos/domain/video.js';

// Playlists d'un utilisateur (ADR-019, partie 3) : privées, seul leur propriétaire les voit ;
// vidéos dans l'ordre d'ajout, sans réordonnancement.

export const PLAYLIST_TITLE_MAX_LENGTH = 100;
export const MAX_PLAYLISTS_PER_USER = 50;
export const MAX_VIDEOS_PER_PLAYLIST = 200;

// Une playlist dans la liste de l'utilisateur. [thumbnailUrl] : miniature de sa première vidéo
// visible, null si elle est vide. [containsVideo] : présent quand la liste est demandée pour une
// vidéo précise (feuille « Enregistrer »).
export interface PlaylistSummary {
  id: string;
  title: string;
  videoCount: number;
  thumbnailUrl: string | null;
  updatedAt: Date;
  containsVideo?: boolean;
}

// Contenu d'une playlist : ses vidéos visibles par l'utilisateur, de la plus ancienne ajoutée à
// la plus récente. Une vidéo devenue privée est filtrée, sa ligne est conservée (ADR-016).
export interface PlaylistDetail {
  id: string;
  title: string;
  updatedAt: Date;
  videos: Video[];
}
