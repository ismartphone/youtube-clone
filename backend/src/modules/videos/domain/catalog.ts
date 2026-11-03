import type { CategorySummary, Video, VideoFormat } from './video.js';
import type { VideoVisibility } from './visibility.js';

// Administration du catalogue, réservée au back-office (ADR-005).
// La base ne stocke que les URL des fichiers : pas d'import pour l'instant, les URL sont saisies à la main.
// Sans URL de miniature, le serveur en génère une à partir de la vidéo (ThumbnailGenerator).

// Vidéo telle qu'enregistrée : la miniature est toujours renseignée.
export interface VideoInput {
  title: string;
  description: string | null;
  channelId: string;
  categoryId: string | null;
  format: VideoFormat;
  isPremium: boolean;
  // Absents à la création : publique, commentaires activés (ADR-016).
  visibility?: VideoVisibility;
  commentsEnabled?: boolean;
  videoUrl: string;
  thumbnailUrl: string;
  durationSeconds: number;
}

// Vidéo envoyée par l'administrateur : thumbnailUrl null demande une miniature générée.
export type VideoDraft = Omit<VideoInput, 'thumbnailUrl'> & {
  thumbnailUrl: string | null;
};

// Modification partielle : un champ absent n'est pas modifié, null efface description ou catégorie,
// et thumbnailUrl null demande une miniature générée.
export type VideoPatch = Partial<Omit<VideoInput, 'thumbnailUrl'>> & {
  thumbnailUrl?: string | null;
};

// Modification écrite en base, miniature déjà résolue.
export type VideoChanges = Partial<VideoInput>;

export interface CategoryWithCount extends CategorySummary {
  videoCount: number;
}

export interface CatalogCounts {
  users: number;
  channels: number;
  videos: number;
  // Vues déjà reportées en base, sans celles encore en attente dans le compteur.
  storedViews: number;
}

export const VIDEO_CATALOG = Symbol('VIDEO_CATALOG');
export const CATEGORY_REPOSITORY = Symbol('CATEGORY_REPOSITORY');
export const CATALOG_STATS = Symbol('CATALOG_STATS');

// Les méthodes lèvent UnknownChannelError ou UnknownCategoryError si une référence n'existe pas.
export interface VideoCatalog {
  create(input: VideoInput): Promise<Video>;
  // Renvoie null si la vidéo n'existe pas.
  update(id: string, changes: VideoChanges): Promise<Video | null>;
  // Renvoie false si la vidéo n'existe pas.
  delete(id: string): Promise<boolean>;
}

export interface CategoryRepository {
  // Triées par nom.
  findAll(): Promise<CategoryWithCount[]>;
  // Lève CategoryAlreadyExistsError si le nom est déjà pris.
  create(name: string): Promise<CategorySummary>;
  // Les vidéos de la catégorie restent, sans catégorie. Renvoie false si elle n'existe pas.
  delete(id: string): Promise<boolean>;
}

// Lecture seule sur plusieurs tables : acceptable dans un monolithe à base unique (ADR-003).
export interface CatalogStatsReader {
  counts(): Promise<CatalogCounts>;
}
