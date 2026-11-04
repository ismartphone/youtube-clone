// Modèles du domaine des commentaires, indépendants de Prisma et de NestJS.

// Auteur tel qu'affiché sous la vidéo : jamais son email ni son rôle.
export interface CommentAuthor {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  // @handle de sa chaîne, pour préremplir une réponse ; null sans chaîne (ADR-023).
  handle: string | null;
}

// Avis de l'utilisateur sur un commentaire (ADR-023, sur le modèle de LIKE, ADR-019).
export type CommentReaction = 'LIKE' | 'DISLIKE';

export interface Comment {
  id: string;
  videoId: string;
  // Commentaire de premier niveau auquel répond celui-ci ; null pour un commentaire.
  parentId: string | null;
  content: string;
  createdAt: Date;
  author: CommentAuthor;
  likeCount: number;
  // Nombre de réponses ; toujours 0 pour une réponse (un seul niveau).
  replyCount: number;
  // Cœur du propriétaire de la chaîne.
  heartedByCreator: boolean;
  // Avis de l'utilisateur qui lit ; null pour un visiteur ou sans avis.
  reaction: CommentReaction | null;
}

export interface CommentPage {
  items: Comment[];
  page: number;
  pageSize: number;
  total: number;
}

// « Top commentaires » (par défaut) : « j'aime » décroissants ; « Les plus récents » : date.
export const COMMENT_SORTS = ['TOP', 'NEWEST'] as const;
export type CommentSort = (typeof COMMENT_SORTS)[number];

export const COMMENT_MAX_LENGTH = 1000;
// Réponses par page sous un commentaire (ADR-023, décision 7).
export const REPLIES_PAGE_SIZE = 10;
