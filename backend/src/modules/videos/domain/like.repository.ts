import type { Video } from './video.js';
import type { Page, Pagination } from './video.repository.js';

// « J'aime » d'un utilisateur sur une vidéo. Chaque ligne est écrite tout de suite en base :
// c'est elle qui dit qui a aimé quoi. Seul le compteur passe par le tampon Redis.

export const LIKE_REPOSITORY = Symbol('LIKE_REPOSITORY');

export interface LikeRepository {
  // Renvoient true si l'état a changé : aimer deux fois ne compte qu'une fois.
  add(userId: string, videoId: string): Promise<boolean>;
  remove(userId: string, videoId: string): Promise<boolean>;
  isLiked(userId: string, videoId: string): Promise<boolean>;
  // Vidéos aimées par l'utilisateur, de la plus récemment aimée à la plus ancienne.
  findLikedPage(userId: string, pagination: Pagination): Promise<Page<Video>>;
}
