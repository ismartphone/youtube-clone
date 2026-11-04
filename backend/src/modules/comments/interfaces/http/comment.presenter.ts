import type { Comment } from '../../domain/comment.js';

// Forme exacte d'un commentaire ou d'une réponse, dans les listes comme dans les événements
// comment:new et comment:reply.
export function presentComment(comment: Comment) {
  return {
    id: comment.id,
    videoId: comment.videoId,
    parentId: comment.parentId,
    content: comment.content,
    createdAt: comment.createdAt,
    author: {
      id: comment.author.id,
      displayName: comment.author.displayName,
      avatarUrl: comment.author.avatarUrl,
      handle: comment.author.handle,
    },
    likeCount: comment.likeCount,
    replyCount: comment.replyCount,
    heartedByCreator: comment.heartedByCreator,
    reaction: comment.reaction,
  };
}
