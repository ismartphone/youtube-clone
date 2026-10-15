import type { Comment, CommentPage } from './comment.js';

export const COMMENT_REPOSITORY = Symbol('COMMENT_REPOSITORY');

export interface CommentRepository {
  videoExists(videoId: string): Promise<boolean>;
  // Du plus récent au plus ancien.
  findPage(
    videoId: string,
    pagination: { page: number; pageSize: number },
  ): Promise<CommentPage>;
  create(videoId: string, authorId: string, content: string): Promise<Comment>;
}
