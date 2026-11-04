// Modèles du domaine des chaînes, indépendants de Prisma et de NestJS.

export interface ChannelSummary {
  id: string;
  name: string;
  handle: string;
  avatarUrl: string | null;
  bannerUrl: string | null;
}

export interface NewChannel {
  name: string;
  handle: string;
  description: string | null;
}

// Cloche d'un abonnement (ADR-020) : ALL, toutes les publications ; PERSONALIZED, vidéos
// classiques seulement, sans les Shorts (réglage par défaut) ; NONE, rien.
export const NOTIFICATION_LEVELS = ['ALL', 'PERSONALIZED', 'NONE'] as const;
export type NotificationLevel = (typeof NOTIFICATION_LEVELS)[number];

// Abonnés d'une chaîne (FOLLOW, abonnement gratuit), vus par l'utilisateur de la requête.
// isFollowing vaut false pour un visiteur ; notificationLevel est null sans abonnement.
export interface FollowStatus {
  followerCount: number;
  isFollowing: boolean;
  notificationLevel: NotificationLevel | null;
}

// Ce que la page de chaîne montre à l'utilisateur de la requête. isOwner masque le bouton
// S'abonner côté client ; l'interdiction elle-même est vérifiée par l'API.
export interface ViewerChannelStatus extends FollowStatus {
  isOwner: boolean;
}

// Chaîne du créateur, telle qu'il la personnalise (ADR-018) : avec sa description.
export interface OwnChannel extends ChannelSummary {
  description: string | null;
}

// Modification de sa chaîne par le créateur : un champ absent n'est pas modifié, une description
// null est effacée. Mêmes limites qu'à la création.
export interface ChannelChanges {
  name?: string;
  handle?: string;
  description?: string | null;
}

export const CHANNEL_NAME_MAX_LENGTH = 50;
export const CHANNEL_DESCRIPTION_MAX_LENGTH = 1000;

// Photo de profil et bannière envoyées depuis l'application : JPEG ou PNG de 5 Mo au plus,
// recadrés par le serveur (ADR-018).
export const CHANNEL_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
export type ChannelImageKind = 'photo' | 'banner' | 'adLogo';

// Un handle est unique sans tenir compte de la casse : il est stocké en minuscules, sans « @ ».
export const HANDLE_PATTERN = /^[a-z0-9._-]{3,30}$/;

export function normalizeHandle(handle: string): string {
  return handle.trim().replace(/^@/, '').toLowerCase();
}
