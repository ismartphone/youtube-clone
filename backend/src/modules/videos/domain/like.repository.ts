import type { Video } from './video.js';
import type { Page, Pagination } from './video.repository.js';

// Avis d'un utilisateur sur une vidéo : « J'aime » ou « Je n'aime pas » (ADR-019). Chaque ligne
// est écrite tout de suite en base : c'est elle qui dit qui a aimé quoi. Seul le compteur de
// « j'aime » passe par le tampon Redis ; les « Je n'aime pas » ne sont jamais comptés.

export const LIKE_REPOSITORY = Symbol('LIKE_REPOSITORY');

export type Reaction = 'LIKE' | 'DISLIKE';

/**
 * Variation du compteur de « j'aime » quand l'avis passe de [previous] à [next] : un « j'aime »
 * remplacé par un avis négatif ou retiré décompte, un « j'aime » donné compte.
 */
export function likeDelta(
  previous: Reaction | null,
  next: Reaction | null,
): number {
  return (next === 'LIKE' ? 1 : 0) - (previous === 'LIKE' ? 1 : 0);
}

export interface LikeRepository {
  // Pose l'avis [reaction] (null le retire) et renvoie l'avis précédent : les deux s'excluent,
  // une seule ligne par utilisateur et par vidéo.
  react(
    userId: string,
    videoId: string,
    reaction: Reaction | null,
  ): Promise<Reaction | null>;
  // Avis de l'utilisateur, ou null s'il n'en a pas donné.
  reactionOf(userId: string, videoId: string): Promise<Reaction | null>;
  // Vidéos aimées par l'utilisateur, de la plus récemment aimée à la plus ancienne.
  findLikedPage(userId: string, pagination: Pagination): Promise<Page<Video>>;
}
