import type { Comment } from '../../domain/comment.js';

// Forme exacte d'un commentaire, dans la liste comme dans l'événement comment:new.
export function presentComment(comment: Comment) {
  return {
    id: comment.id,
    videoId: comment.videoId,
    content: comment.content,
    createdAt: comment.createdAt,
    author: {
      id: comment.author.id,
      displayName: comment.author.displayName,
      avatarUrl: comment.author.avatarUrl,
    },
  };
}
