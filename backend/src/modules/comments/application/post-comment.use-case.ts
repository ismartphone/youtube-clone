import type { VideoRoomPublisher } from '../../realtime/domain/video-room-publisher.js';
import type { Comment } from '../domain/comment.js';
import type { CommentRepository } from '../domain/ports.js';
import {
  canReachComments,
  CommentsDisabledError,
  threadRootId,
} from '../domain/comment-rules.js';

// Publie un commentaire ou une réponse, puis le diffuse aux spectateurs de la vidéo :
// comment:new pour un commentaire (ADR-004), comment:reply pour une réponse (ADR-023).
export class PostCommentUseCase {
  constructor(
    private readonly comments: CommentRepository,
    private readonly publisher: VideoRoomPublisher,
  ) {}

  // Renvoie null si la vidéo n'existe pas (ou est privée et n'appartient pas à l'auteur), ou si
  // [replyTo] n'est pas un commentaire de cette vidéo. Lève CommentsDisabledError si les
  // commentaires sont désactivés.
  async execute(
    videoId: string,
    authorId: string,
    content: string,
    replyTo: string | null = null,
  ): Promise<Comment | null> {
    const target = await this.comments.findTarget(videoId);
    if (!canReachComments(target, authorId)) {
      return null;
    }
    if (!target.commentsEnabled) {
      throw new CommentsDisabledError();
    }
    let parentId: string | null = null;
    if (replyTo) {
      const parent = await this.comments.findRef(replyTo);
      if (!parent || parent.videoId !== videoId) {
        return null;
      }
      parentId = threadRootId(parent);
    }
    const comment = await this.comments.create(
      videoId,
      authorId,
      content.trim(),
      parentId,
    );
    // Le commentaire du domaine ne contient que des champs publics (auteur : nom, avatar et
    // @handle), et sa forme JSON est celle de la liste : le client l'ajoute tel quel. Son avis
    // vaut null : chacun relit le sien avec la liste.
    this.publisher.publish(
      videoId,
      parentId ? 'comment:reply' : 'comment:new',
      comment,
    );
    return comment;
  }
}
