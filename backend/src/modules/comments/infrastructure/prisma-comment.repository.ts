import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { Comment, CommentPage } from '../domain/comment.js';
import type { CommentRepository } from '../domain/ports.js';

// Sélection explicite : de l'auteur, seuls son nom affiché et son avatar sortent de la base.
const COMMENT_SELECT = {
  id: true,
  videoId: true,
  content: true,
  createdAt: true,
  user: { select: { id: true, displayName: true, avatarUrl: true } },
} satisfies Prisma.CommentSelect;

type CommentRow = Prisma.CommentGetPayload<{ select: typeof COMMENT_SELECT }>;

function toComment({ user, ...comment }: CommentRow): Comment {
  return { ...comment, author: user };
}

export class PrismaCommentRepository implements CommentRepository {
  constructor(private readonly prisma: PrismaService) {}

  async videoExists(videoId: string): Promise<boolean> {
    const video = await this.prisma.video.findUnique({
      where: { id: videoId },
      select: { id: true },
    });
    return video !== null;
  }

  // Index (videoId, createdAt DESC) prévu dans le schéma pour cette requête.
  async findPage(
    videoId: string,
    { page, pageSize }: { page: number; pageSize: number },
  ): Promise<CommentPage> {
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.comment.findMany({
        where: { videoId },
        select: COMMENT_SELECT,
        orderBy: [{ createdAt: 'desc' }, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.comment.count({ where: { videoId } }),
    ]);
    return { items: rows.map(toComment), page, pageSize, total };
  }

  async create(
    videoId: string,
    authorId: string,
    content: string,
  ): Promise<Comment> {
    const row = await this.prisma.comment.create({
      data: { videoId, userId: authorId, content },
      select: COMMENT_SELECT,
    });
    return toComment(row);
  }
}
