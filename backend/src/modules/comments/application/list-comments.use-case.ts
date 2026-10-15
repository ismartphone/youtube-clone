import type { CommentPage } from '../domain/comment.js';
import type { CommentRepository } from '../domain/ports.js';

export class ListCommentsUseCase {
  constructor(private readonly comments: CommentRepository) {}

  // Renvoie null si la vidéo n'existe pas.
  async execute(
    videoId: string,
    pagination: { page: number; pageSize: number },
  ): Promise<CommentPage | null> {
    if (!(await this.comments.videoExists(videoId))) {
      return null;
    }
    return this.comments.findPage(videoId, pagination);
  }
}
