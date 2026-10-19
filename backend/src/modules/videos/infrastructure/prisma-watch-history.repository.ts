import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { Video } from '../domain/video.js';
import type { Page, Pagination } from '../domain/video.repository.js';
import type { WatchHistoryRepository } from '../domain/watch-history.repository.js';
import { VIDEO_SELECT } from './prisma-video.repository.js';

export class PrismaWatchHistoryRepository implements WatchHistoryRepository {
  constructor(private readonly prisma: PrismaService) {}

  async record(userId: string, videoId: string): Promise<void> {
    const watchedAt = new Date();
    await this.prisma.watchHistory.upsert({
      where: { userId_videoId: { userId, videoId } },
      create: { userId, videoId, watchedAt },
      update: { watchedAt },
    });
  }

  async findPage(
    userId: string,
    { page, pageSize }: Pagination,
  ): Promise<Page<Video>> {
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.watchHistory.findMany({
        where: { userId },
        select: { video: { select: VIDEO_SELECT } },
        orderBy: [{ watchedAt: 'desc' }, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.watchHistory.count({ where: { userId } }),
    ]);
    return { items: rows.map((row) => row.video), page, pageSize, total };
  }
}
