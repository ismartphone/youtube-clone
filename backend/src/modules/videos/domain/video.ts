// Modèles de lecture du domaine vidéo, indépendants de Prisma et de NestJS.

export const VIDEO_FORMATS = ['SHORT', 'LONG'] as const;
export type VideoFormat = (typeof VIDEO_FORMATS)[number];

export interface CategorySummary {
  id: string;
  name: string;
}

export interface ChannelSummary {
  id: string;
  name: string;
  handle: string;
  avatarUrl: string | null;
}

export interface Video {
  id: string;
  title: string;
  description: string | null;
  videoUrl: string;
  thumbnailUrl: string;
  durationSeconds: number;
  format: VideoFormat;
  isPremium: boolean;
  viewCount: number;
  publishedAt: Date;
  channel: ChannelSummary;
  category: CategorySummary | null;
}

export interface Channel extends ChannelSummary {
  description: string | null;
  createdAt: Date;
  videos: Video[];
}

// Spectateur de la requête. Sans authentification, personne n'est encore Premium.
export interface Viewer {
  isPremium: boolean;
}

export const ANONYMOUS_VIEWER: Viewer = { isPremium: false };

// Administrateur du back-office : il voit l'URL de toutes les vidéos, exclusives comprises.
export const ADMIN_VIEWER: Viewer = { isPremium: true };

// Règle d'accès (CLAUDE.md, ADR-007) : l'URL d'une vidéo exclusive n'est donnée
// qu'à un spectateur Premium, et cette vérification se fait toujours côté serveur.
export function playableUrl(video: Video, viewer: Viewer): string | null {
  return video.isPremium && !viewer.isPremium ? null : video.videoUrl;
}
