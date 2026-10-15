import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { LikeRepository } from '../domain/like.repository.js';

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
}
