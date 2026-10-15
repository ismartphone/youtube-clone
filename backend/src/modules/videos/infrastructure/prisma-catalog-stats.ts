import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { CatalogCounts, CatalogStatsReader } from '../domain/catalog.js';

export class PrismaCatalogStats implements CatalogStatsReader {
  constructor(private readonly prisma: PrismaService) {}

  async counts(): Promise<CatalogCounts> {
    const [users, channels, videos, views] = await this.prisma.$transaction([
      this.prisma.user.count(),
      this.prisma.channel.count(),
      this.prisma.video.count(),
      this.prisma.video.aggregate({ _sum: { viewCount: true } }),
    ]);
    return {
      users,
      channels,
      videos,
      storedViews: views._sum.viewCount ?? 0,
    };
  }
}
