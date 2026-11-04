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

// Cœur posé ou retiré par quelqu'un d'autre que le propriétaire de la chaîne (ADR-023).
export class NotVideoOwnerError extends Error {
  constructor() {
    super('Seul le propriétaire de la chaîne peut mettre un cœur.');
  }
}

// Un seul niveau de réponses (ADR-023) : répondre à une réponse range la nouvelle réponse dans
// le même fil, sous le commentaire de premier niveau.
export function threadRootId(comment: {
  id: string;
  parentId: string | null;
}): string {
  return comment.parentId ?? comment.id;
}
