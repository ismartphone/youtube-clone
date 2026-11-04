import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type {
  Comment,
  CommentPage,
  CommentReaction,
  CommentSort,
} from '../domain/comment.js';
import type {
  CommentRef,
  CommentRepository,
  CommentTarget,
} from '../domain/ports.js';

// Sélection explicite : de l'auteur, seuls son nom affiché, son avatar et le @handle de sa
// chaîne sortent de la base. [viewerId] : l'avis de l'utilisateur qui lit, et lui seul.
function commentSelect(viewerId: string | null) {
  return {
    id: true,
    videoId: true,
    parentId: true,
    content: true,
    createdAt: true,
    likeCount: true,
    creatorHeartedAt: true,
    user: {
      select: {
        id: true,
        displayName: true,
        avatarUrl: true,
        channel: { select: { handle: true } },
      },
    },
    _count: { select: { replies: true } },
    likes: {
      where: { userId: viewerId ?? undefined },
      select: { type: true },
      take: viewerId ? 1 : 0,
    },
  } satisfies Prisma.CommentSelect;
}

type CommentRow = Prisma.CommentGetPayload<{
  select: ReturnType<typeof commentSelect>;
}>;

function toComment(row: CommentRow): Comment {
  return {
    id: row.id,
    videoId: row.videoId,
    parentId: row.parentId,
    content: row.content,
    createdAt: row.createdAt,
    author: {
      id: row.user.id,
      displayName: row.user.displayName,
      avatarUrl: row.user.avatarUrl,
      handle: row.user.channel?.handle ?? null,
    },
    likeCount: row.likeCount,
    replyCount: row._count.replies,
    heartedByCreator: row.creatorHeartedAt !== null,
    reaction: row.likes[0]?.type ?? null,
  };
}

// « Top » : « j'aime » décroissants, puis date décroissante, puis id, pour un ordre total et
// stable (ADR-023). « Les plus récents » : date, puis id. Index prévus dans le schéma.
function orderBy(sort: CommentSort): Prisma.CommentOrderByWithRelationInput[] {
  return sort === 'TOP'
    ? [{ likeCount: 'desc' }, { createdAt: 'desc' }, { id: 'asc' }]
    : [{ createdAt: 'desc' }, { id: 'asc' }];
}

const TARGET_SELECT = {
  visibility: true,
  commentsEnabled: true,
  channel: { select: { ownerId: true } },
} satisfies Prisma.VideoSelect;

function toTarget(
  video: Prisma.VideoGetPayload<{ select: typeof TARGET_SELECT }>,
): CommentTarget {
  return {
    visibility: video.visibility,
    commentsEnabled: video.commentsEnabled,
    ownerId: video.channel.ownerId,
  };
}

export class PrismaCommentRepository implements CommentRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findTarget(videoId: string): Promise<CommentTarget | null> {
    const video = await this.prisma.video.findUnique({
      where: { id: videoId },
      select: TARGET_SELECT,
    });
    return video && toTarget(video);
  }

  async findRef(commentId: string): Promise<CommentRef | null> {
    const comment = await this.prisma.comment.findUnique({
      where: { id: commentId },
      select: {
        id: true,
        videoId: true,
        parentId: true,
        video: { select: TARGET_SELECT },
      },
    });
    return (
      comment && {
        id: comment.id,
        videoId: comment.videoId,
        parentId: comment.parentId,
        target: toTarget(comment.video),
      }
    );
  }

  async findPage(
    videoId: string,
    {
      page,
      pageSize,
      sort,
    }: { page: number; pageSize: number; sort: CommentSort },
    viewerId: string | null,
  ): Promise<CommentPage> {
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.comment.findMany({
        where: { videoId, parentId: null },
        select: commentSelect(viewerId),
        orderBy: orderBy(sort),
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      // Commentaires et réponses : le nombre affiché sous la vidéo.
      this.prisma.comment.count({ where: { videoId } }),
    ]);
    return { items: rows.map(toComment), page, pageSize, total };
  }

  async findReplies(
    parentId: string,
    { page, pageSize }: { page: number; pageSize: number },
    viewerId: string | null,
  ): Promise<CommentPage> {
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.comment.findMany({
        where: { parentId },
        select: commentSelect(viewerId),
        // De la plus ancienne à la plus récente, comme sur YouTube (ADR-023, décision 3).
        orderBy: [{ createdAt: 'asc' }, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.comment.count({ where: { parentId } }),
    ]);
    return { items: rows.map(toComment), page, pageSize, total };
  }

  async create(
    videoId: string,
    authorId: string,
    content: string,
    parentId: string | null,
  ): Promise<Comment> {
    const row = await this.prisma.comment.create({
      data: { videoId, userId: authorId, content, parentId },
      select: commentSelect(null),
    });
    return toComment(row);
  }

  // Une ligne par utilisateur et par commentaire : un « j'aime » remplace un « je n'aime pas » et
  // inversement. likeCount suit dans la même transaction, pour rester exact.
  async react(
    commentId: string,
    userId: string,
    reaction: CommentReaction,
    on: boolean,
  ): Promise<{ likeCount: number; reaction: CommentReaction | null }> {
    return this.prisma.$transaction(async (tx) => {
      const key = { userId_commentId: { userId, commentId } };
      const before =
        (
          await tx.commentLike.findUnique({
            where: key,
            select: { type: true },
          })
        )?.type ?? null;
      let after: CommentReaction | null = before;
      if (on && before !== reaction) {
        await tx.commentLike.upsert({
          where: key,
          create: { userId, commentId, type: reaction },
          update: { type: reaction },
        });
        after = reaction;
      } else if (!on && before === reaction) {
        await tx.commentLike.delete({ where: key });
        after = null;
      }
      const delta = (after === 'LIKE' ? 1 : 0) - (before === 'LIKE' ? 1 : 0);
      const { likeCount } = await tx.comment.update({
        where: { id: commentId },
        data: delta === 0 ? {} : { likeCount: { increment: delta } },
        select: { likeCount: true },
      });
      return { likeCount, reaction: after };
    });
  }

  async setHeart(commentId: string, hearted: boolean): Promise<void> {
    await this.prisma.comment.update({
      where: { id: commentId },
      data: { creatorHeartedAt: hearted ? new Date() : null },
    });
  }
}
