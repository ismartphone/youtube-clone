import type { VideoRoomPublisher } from '../../realtime/domain/video-room-publisher.js';
import type { Comment } from '../domain/comment.js';
import type { CommentRepository } from '../domain/ports.js';
import {
  canReachComments,
  CommentsDisabledError,
} from '../domain/comment-rules.js';

// Publie un commentaire, puis le diffuse aux spectateurs de la vidéo (comment:new, ADR-004).
export class PostCommentUseCase {
  constructor(
    private readonly comments: CommentRepository,
    private readonly publisher: VideoRoomPublisher,
  ) {}

  // Renvoie null si la vidéo n'existe pas (ou est privée et n'appartient pas à l'auteur).
  // Lève CommentsDisabledError si les commentaires sont désactivés.
  async execute(
    videoId: string,
    authorId: string,
    content: string,
  ): Promise<Comment | null> {
    const target = await this.comments.findTarget(videoId);
    if (!canReachComments(target, authorId)) {
      return null;
    }
    if (!target.commentsEnabled) {
      throw new CommentsDisabledError();
    }
    const comment = await this.comments.create(
      videoId,
      authorId,
      content.trim(),
    );
    // Le commentaire du domaine ne contient que des champs publics (auteur : nom et avatar),
    // et sa forme JSON est celle de GET /videos/:id/comments : le client l'ajoute tel quel.
    this.publisher.publish(videoId, 'comment:new', comment);
    return comment;
  }
}
