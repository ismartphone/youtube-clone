// Modèles du domaine des chaînes, indépendants de Prisma et de NestJS.

export interface ChannelSummary {
  id: string;
  name: string;
  handle: string;
  avatarUrl: string | null;
}

export interface NewChannel {
  name: string;
  handle: string;
}

// Abonnés d'une chaîne (FOLLOW, abonnement gratuit), vus par l'utilisateur de la requête.
// isFollowing vaut false pour un visiteur.
export interface FollowStatus {
  followerCount: number;
  isFollowing: boolean;
}

// Ce que la page de chaîne montre à l'utilisateur de la requête. isOwner masque le bouton
// S'abonner côté client ; l'interdiction elle-même est vérifiée par l'API.
export interface ViewerChannelStatus extends FollowStatus {
  isOwner: boolean;
}

// Un handle est unique sans tenir compte de la casse : il est stocké en minuscules, sans « @ ».
export const HANDLE_PATTERN = /^[a-z0-9._-]{3,30}$/;

export function normalizeHandle(handle: string): string {
  return handle.trim().replace(/^@/, '').toLowerCase();
}
