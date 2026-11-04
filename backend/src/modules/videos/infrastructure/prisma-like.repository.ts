import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { LikeRepository, Reaction } from '../domain/like.repository.js';
import type { Video } from '../domain/video.js';
import type { Page, Pagination } from '../domain/video.repository.js';
import { VIDEO_SELECT } from './prisma-video.repository.js';

// Une vidéo privée n'apparaît que dans les listes de son propriétaire (ADR-016) : filtre à la
// lecture, les lignes restent en base.
function visibleTo(userId: string): Prisma.VideoWhereInput {
  return {
    OR: [{ visibility: { not: 'PRIVATE' } }, { channel: { ownerId: userId } }],
  };
}

export class PrismaLikeRepository implements LikeRepository {
  constructor(private readonly prisma: PrismaService) {}

  // Lecture et écriture dans une transaction : deux requêtes simultanées ne faussent pas l'avis
  // précédent, dont dépend le compteur de « j'aime ».
  react(
    userId: string,
    videoId: string,
    reaction: Reaction | null,
  ): Promise<Reaction | null> {
    return this.prisma.$transaction(async (tx) => {
      const key = { userId_videoId: { userId, videoId } };
      const previous = await tx.like.findUnique({
        where: key,
        select: { type: true },
      });
      if (reaction === null) {
        if (previous) await tx.like.delete({ where: key });
      } else if (!previous) {
        await tx.like.create({ data: { userId, videoId, type: reaction } });
      } else if (previous.type !== reaction) {
        // Passer d'un avis à l'autre date l'avis du moment où il est donné.
        await tx.like.update({
          where: key,
          data: { type: reaction, createdAt: new Date() },
        });
      }
      return previous?.type ?? null;
    });
  }

  async reactionOf(userId: string, videoId: string): Promise<Reaction | null> {
    const like = await this.prisma.like.findUnique({
      where: { userId_videoId: { userId, videoId } },
      select: { type: true },
    });
    return like?.type ?? null;
  }

  async findLikedPage(
    userId: string,
    { page, pageSize }: Pagination,
  ): Promise<Page<Video>> {
    // Seuls les « j'aime » : un avis négatif n'ajoute pas la vidéo à la liste.
    const where = { userId, type: 'LIKE' as const, video: visibleTo(userId) };
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.like.findMany({
        where,
        select: { video: { select: VIDEO_SELECT } },
        orderBy: [{ createdAt: 'desc' }, { videoId: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.like.count({ where }),
    ]);
    return { items: rows.map((row) => row.video), page, pageSize, total };
  }
}
