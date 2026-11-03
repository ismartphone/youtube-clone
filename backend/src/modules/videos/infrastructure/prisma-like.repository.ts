import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { LikeRepository } from '../domain/like.repository.js';
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

  async add(userId: string, videoId: string): Promise<boolean> {
    const { count } = await this.prisma.like.createMany({
      data: { userId, videoId },
      skipDuplicates: true,
    });
    return count > 0;
  }

  async remove(userId: string, videoId: string): Promise<boolean> {
    const { count } = await this.prisma.like.deleteMany({
      where: { userId, videoId },
    });
    return count > 0;
  }

  async isLiked(userId: string, videoId: string): Promise<boolean> {
    const like = await this.prisma.like.findUnique({
      where: { userId_videoId: { userId, videoId } },
      select: { userId: true },
    });
    return like !== null;
  }

  async findLikedPage(
    userId: string,
    { page, pageSize }: Pagination,
  ): Promise<Page<Video>> {
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.like.findMany({
        where: { userId, video: visibleTo(userId) },
        select: { video: { select: VIDEO_SELECT } },
        orderBy: [{ createdAt: 'desc' }, { videoId: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.like.count({
        where: { userId, video: visibleTo(userId) },
      }),
    ]);
    return { items: rows.map((row) => row.video), page, pageSize, total };
  }
}
