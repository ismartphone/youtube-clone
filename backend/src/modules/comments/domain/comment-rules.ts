import { canAccessVideo } from '../../videos/domain/visibility.js';
import type { CommentTarget } from './ports.js';

// Commentaires désactivés (ADR-016) : personne ne commente, le propriétaire compris.
export class CommentsDisabledError extends Error {
  constructor() {
    super('Les commentaires sont désactivés pour cette vidéo.');
  }
}

// Une vidéo privée n'existe, pour ses commentaires aussi, que pour son propriétaire.
export function canReachComments(
  target: CommentTarget | null,
  userId: string | null,
): target is CommentTarget {
  return (
    target !== null && canAccessVideo(target.visibility, target.ownerId, userId)
  );
}
