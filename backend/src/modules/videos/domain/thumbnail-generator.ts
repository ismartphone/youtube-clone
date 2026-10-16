import type { VideoFormat } from './video.js';

// Génération des miniatures côté serveur : une image extraite de la vidéo (ADR-002, stockage local
// en développement). L'URL renvoyée est un chemin relatif (/media/thumbnails/...), que chaque client
// complète avec l'adresse par laquelle il joint l'API.

export const THUMBNAIL_GENERATOR = Symbol('THUMBNAIL_GENERATOR');

// Position de l'image extraite : 10 % de la durée, pour éviter un écran noir de début de vidéo.
export const THUMBNAIL_POSITION_RATIO = 0.1;

export function thumbnailPosition(durationSeconds: number): number {
  return Math.max(0, durationSeconds * THUMBNAIL_POSITION_RATIO);
}

export interface ThumbnailSize {
  width: number;
  height: number;
}

// 1280x720 pour une vidéo classique. Un Short est vertical : même taille, en portrait,
// comme ses miniatures affichées dans l'onglet Shorts.
export function thumbnailSize(format: VideoFormat): ThumbnailSize {
  return format === 'SHORT'
    ? { width: 720, height: 1280 }
    : { width: 1280, height: 720 };
}

// Miniature de remplacement (seed) : pas une vraie image de la vidéo, à régénérer.
export function isPlaceholderThumbnail(thumbnailUrl: string): boolean {
  try {
    return new URL(thumbnailUrl).hostname === 'placehold.co';
  } catch {
    return false; // chemin relatif : miniature générée par le serveur
  }
}

export interface ThumbnailGenerator {
  /**
   * Extrait une image de la vidéo à [atSeconds], la recadre à [size] sans la déformer,
   * l'enregistre en JPEG et renvoie son URL.
   * Lève ThumbnailUnavailableError si FFmpeg est absent, ThumbnailGenerationError si l'extraction échoue.
   */
  generate(
    videoUrl: string,
    atSeconds: number,
    size: ThumbnailSize,
  ): Promise<string>;
  // Vrai si la miniature a été générée par le serveur (et peut donc être supprimée ou remplacée).
  isGenerated(thumbnailUrl: string): boolean;
  // Supprime le fichier d'une miniature générée ; sans effet pour une URL externe.
  remove(thumbnailUrl: string): Promise<void>;
}

export class ThumbnailUnavailableError extends Error {
  constructor() {
    super(
      "FFmpeg est introuvable sur le serveur : la miniature ne peut pas être générée. Saisissez l'URL d'une miniature.",
    );
  }
}

export class ThumbnailGenerationError extends Error {
  constructor(detail: string) {
    super(`Impossible d'extraire une image de cette vidéo : ${detail}`);
  }
}
