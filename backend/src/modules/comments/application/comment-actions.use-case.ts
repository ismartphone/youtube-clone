import type { CommentReaction } from '../domain/comment.js';
import {
  canReachComments,
  CommentsDisabledError,
  NotVideoOwnerError,
} from '../domain/comment-rules.js';
import type { CommentRef, CommentRepository } from '../domain/ports.js';

// Commentaire atteignable par l'utilisateur et ouvert aux actions ; null s'il n'existe pas pour
// lui. Lève CommentsDisabledError si les commentaires de la vidéo sont désactivés.
async function findActionable(
  comments: CommentRepository,
  commentId: string,
  userId: string,
): Promise<CommentRef | null> {
  const comment = await comments.findRef(commentId);
  if (!comment || !canReachComments(comment.target, userId)) {
    return null;
  }
  if (!comment.target.commentsEnabled) {
    throw new CommentsDisabledError();
  }
  return comment;
}

// « J'aime » et « Je n'aime pas » sur un commentaire ou une réponse (ADR-023). Pas de temps réel.
export class ReactToCommentUseCase {
  constructor(private readonly comments: CommentRepository) {}

  async execute(
    userId: string,
    commentId: string,
    reaction: CommentReaction,
    on: boolean,
  ): Promise<{ likeCount: number; reaction: CommentReaction | null } | null> {
    const comment = await findActionable(this.comments, commentId, userId);
    if (!comment) {
      return null;
    }
    return this.comments.react(comment.id, userId, reaction, on);
  }
}

// Cœur du créateur (ADR-023) : réservé au propriétaire de la chaîne de la vidéo, réponses
// comprises, et bloqué comme le reste quand les commentaires sont désactivés.
export class HeartCommentUseCase {
  constructor(private readonly comments: CommentRepository) {}

  // Renvoie false si le commentaire n'existe pas pour l'utilisateur.
  async execute(
    userId: string,
    commentId: string,
    hearted: boolean,
  ): Promise<boolean> {
    const comment = await findActionable(this.comments, commentId, userId);
    if (!comment) {
      return false;
    }
    if (comment.target.ownerId !== userId) {
      throw new NotVideoOwnerError();
    }
    await this.comments.setHeart(comment.id, hearted);
    return true;
  }
}
