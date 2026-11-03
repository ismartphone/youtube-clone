import type { VideoVisibility } from '../../videos/domain/visibility.js';
import type { Comment, CommentPage } from './comment.js';

export const COMMENT_REPOSITORY = Symbol('COMMENT_REPOSITORY');

// Réglages de la vidéo utiles aux commentaires (ADR-016).
export interface CommentTarget {
  visibility: VideoVisibility;
  ownerId: string;
  commentsEnabled: boolean;
}

export interface CommentRepository {
  // null si la vidéo n'existe pas.
  findTarget(videoId: string): Promise<CommentTarget | null>;
  // Du plus récent au plus ancien.
  findPage(
    videoId: string,
    pagination: { page: number; pageSize: number },
  ): Promise<CommentPage>;
  create(videoId: string, authorId: string, content: string): Promise<Comment>;
}
