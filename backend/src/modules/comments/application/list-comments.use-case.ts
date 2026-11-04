import { canReachComments } from '../domain/comment-rules.js';
import type { CommentPage, CommentSort } from '../domain/comment.js';
import type { CommentRepository } from '../domain/ports.js';

// Liste des commentaires, avec l'indicateur commentsDisabled. Désactivés, ils sont masqués :
// la liste est vide, mais rien n'est supprimé, et tout revient à la réactivation (ADR-016).
export type CommentList = CommentPage & { commentsDisabled: boolean };

export class ListCommentsUseCase {
  constructor(private readonly comments: CommentRepository) {}

  // Renvoie null si la vidéo n'existe pas, ou si elle est privée et que l'utilisateur n'en est
  // pas le propriétaire.
  async execute(
    videoId: string,
    query: { page: number; pageSize: number; sort: CommentSort },
    userId: string | null,
  ): Promise<CommentList | null> {
    const target = await this.comments.findTarget(videoId);
    if (!canReachComments(target, userId)) {
      return null;
    }
    const { page, pageSize } = query;
    if (!target.commentsEnabled) {
      return { items: [], page, pageSize, total: 0, commentsDisabled: true };
    }
    return {
      ...(await this.comments.findPage(videoId, query, userId)),
      commentsDisabled: false,
    };
  }
}

// Réponses d'un commentaire (ADR-023), mêmes règles d'accès que la liste de la vidéo.
export class ListRepliesUseCase {
  constructor(private readonly comments: CommentRepository) {}

  // Renvoie null si le commentaire n'existe pas, si sa vidéo est privée et n'appartient pas à
  // l'utilisateur, ou si les commentaires sont désactivés (ils sont alors masqués, ADR-016).
  async execute(
    commentId: string,
    pagination: { page: number; pageSize: number },
    userId: string | null,
  ): Promise<CommentPage | null> {
    const comment = await this.comments.findRef(commentId);
    if (
      !comment ||
      !canReachComments(comment.target, userId) ||
      !comment.target.commentsEnabled
    ) {
      return null;
    }
    return this.comments.findReplies(comment.id, pagination, userId);
  }
}
