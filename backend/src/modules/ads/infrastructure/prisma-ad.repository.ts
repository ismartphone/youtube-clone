import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { Ad, NewAd } from '../domain/ad.js';
import type { AdRepository } from '../domain/ports.js';

export class PrismaAdRepository implements AdRepository {
  constructor(private readonly prisma: PrismaService) {}

  findAll(): Promise<Ad[]> {
    return this.prisma.ad.findMany({ orderBy: { createdAt: 'desc' } });
  }

  create(ad: NewAd): Promise<Ad> {
    return this.prisma.ad.create({ data: ad });
  }

  async setHlsUrl(id: string, hlsUrl: string): Promise<boolean> {
    const { count } = await this.prisma.ad.updateMany({
      where: { id },
      data: { hlsUrl },
    });
    return count > 0;
  }

  async delete(id: string): Promise<Ad | null> {
    const ad = await this.prisma.ad.findUnique({ where: { id } });
    if (!ad) {
      return null;
    }
    const { count } = await this.prisma.ad.deleteMany({ where: { id } });
    return count > 0 ? ad : null;
  }

  // Une ligne au hasard : quelques publicités au plus, un décalage aléatoire suffit.
  async pickRandom(): Promise<Ad | null> {
    const count = await this.prisma.ad.count();
    if (count === 0) {
      return null;
    }
    return this.prisma.ad.findFirst({
      orderBy: { createdAt: 'asc' },
      skip: Math.floor(Math.random() * count),
    });
  }

  async findVideoUrls(): Promise<string[]> {
    const ads = await this.prisma.ad.findMany({ select: { videoUrl: true } });
    return ads.map((ad) => ad.videoUrl);
  }
}
