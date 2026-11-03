import { canReachComments } from '../domain/comment-rules.js';
import type { CommentPage } from '../domain/comment.js';
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
    pagination: { page: number; pageSize: number },
    userId: string | null,
  ): Promise<CommentList | null> {
    const target = await this.comments.findTarget(videoId);
    if (!canReachComments(target, userId)) {
      return null;
    }
    if (!target.commentsEnabled) {
      return { items: [], ...pagination, total: 0, commentsDisabled: true };
    }
    return {
      ...(await this.comments.findPage(videoId, pagination)),
      commentsDisabled: false,
    };
  }
}
