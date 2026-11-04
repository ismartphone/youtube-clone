import type { VideoVisibility } from '../../videos/domain/visibility.js';
import type {
  Comment,
  CommentPage,
  CommentReaction,
  CommentSort,
} from './comment.js';

export const COMMENT_REPOSITORY = Symbol('COMMENT_REPOSITORY');

// Réglages de la vidéo utiles aux commentaires (ADR-016).
export interface CommentTarget {
  visibility: VideoVisibility;
  ownerId: string;
  commentsEnabled: boolean;
}

// Commentaire visé par une réponse, un avis ou un cœur, avec les réglages de sa vidéo.
export interface CommentRef {
  id: string;
  videoId: string;
  parentId: string | null;
  target: CommentTarget;
}

export interface CommentRepository {
  // null si la vidéo n'existe pas.
  findTarget(videoId: string): Promise<CommentTarget | null>;
  // null si le commentaire n'existe pas.
  findRef(commentId: string): Promise<CommentRef | null>;
  // Commentaires de premier niveau. total compte aussi les réponses : c'est le nombre affiché
  // sous la vidéo (ADR-023). [viewerId] : pour l'avis de l'utilisateur sur chaque commentaire.
  findPage(
    videoId: string,
    query: { page: number; pageSize: number; sort: CommentSort },
    viewerId: string | null,
  ): Promise<CommentPage>;
  // Réponses d'un commentaire, de la plus ancienne à la plus récente.
  findReplies(
    parentId: string,
    pagination: { page: number; pageSize: number },
    viewerId: string | null,
  ): Promise<CommentPage>;
  create(
    videoId: string,
    authorId: string,
    content: string,
    parentId: string | null,
  ): Promise<Comment>;
  // Pose ou retire un avis, et tient likeCount à jour dans la même transaction.
  react(
    commentId: string,
    userId: string,
    reaction: CommentReaction,
    on: boolean,
  ): Promise<{ likeCount: number; reaction: CommentReaction | null }>;
  setHeart(commentId: string, hearted: boolean): Promise<void>;
}
