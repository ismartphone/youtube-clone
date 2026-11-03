// Modèles de lecture du domaine vidéo, indépendants de Prisma et de NestJS.
import { canAccessVideo, type VideoVisibility } from './visibility.js';

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
  // Propriétaire, pour les règles de visibilité (ADR-016) ; jamais exposé par les présentateurs.
  ownerId: string;
}

export interface Video {
  id: string;
  title: string;
  description: string | null;
  videoUrl: string;
  // Manifeste HLS (ADR-017), null tant qu'il n'existe pas : le MP4 sert alors de repli.
  hlsUrl: string | null;
  // Sous-titres WebVTT générés automatiquement (ADR-017), null s'il n'y en a pas.
  subtitleUrl: string | null;
  thumbnailUrl: string;
  durationSeconds: number;
  format: VideoFormat;
  isPremium: boolean;
  visibility: VideoVisibility;
  commentsEnabled: boolean;
  viewCount: number;
  likeCount: number;
  publishedAt: Date;
  channel: ChannelSummary;
  category: CategorySummary | null;
}

export interface Channel extends ChannelSummary {
  description: string | null;
  bannerUrl: string | null;
  createdAt: Date;
  videos: Video[];
}

// Spectateur de la requête. Sans authentification, personne n'est encore Premium.
// userId sert aux règles de visibilité (ADR-016) ; isAdmin : le back-office voit tout.
export interface Viewer {
  isPremium: boolean;
  userId: string | null;
  isAdmin?: boolean;
}

export const ANONYMOUS_VIEWER: Viewer = { isPremium: false, userId: null };

// Administrateur du back-office : il voit l'URL de toutes les vidéos, exclusives comprises.
export const ADMIN_VIEWER: Viewer = {
  isPremium: true,
  userId: null,
  isAdmin: true,
};

// Accès par le lien direct : une vidéo privée n'existe que pour son propriétaire (ADR-016).
export function canSee(
  video: Pick<Video, 'visibility' | 'channel'>,
  viewer: Viewer,
): boolean {
  return (
    viewer.isAdmin === true ||
    canAccessVideo(video.visibility, video.channel.ownerId, viewer.userId)
  );
}

// Règle d'accès (CLAUDE.md, ADR-007) : l'URL d'une vidéo exclusive n'est donnée
// qu'à un spectateur Premium, et cette vérification se fait toujours côté serveur.
export function playableUrl(video: Video, viewer: Viewer): string | null {
  return video.isPremium && !viewer.isPremium ? null : video.videoUrl;
}
