import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { NewPost, Post, PostPage } from '../domain/post.js';
import type { PostChannels, PostRepository } from '../domain/ports.js';

const POST_SELECT = {
  id: true,
  text: true,
  imageUrl: true,
  createdAt: true,
  channel: { select: { id: true, name: true, handle: true, avatarUrl: true } },
} as const;

export class PrismaPostRepository implements PostRepository {
  constructor(private readonly prisma: PrismaService) {}

  create(post: NewPost): Promise<Post> {
    return this.prisma.post.create({ data: post, select: POST_SELECT });
  }

  async findByChannel(
    channelId: string,
    page: number,
    pageSize: number,
  ): Promise<PostPage> {
    const [items, total] = await this.prisma.$transaction([
      this.prisma.post.findMany({
        where: { channelId },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * pageSize,
        take: pageSize,
        select: POST_SELECT,
      }),
      this.prisma.post.count({ where: { channelId } }),
    ]);
    return { items, page, pageSize, total };
  }
}

// Lecture de la table des chaînes depuis le module posts : acceptable dans un monolithe à base
// unique (ADR-003).
export class PrismaPostChannels implements PostChannels {
  constructor(private readonly prisma: PrismaService) {}

  async channelIdOf(userId: string): Promise<string | null> {
    const channel = await this.prisma.channel.findUnique({
      where: { ownerId: userId },
      select: { id: true },
    });
    return channel?.id ?? null;
  }

  async exists(channelId: string): Promise<boolean> {
    return (await this.prisma.channel.count({ where: { id: channelId } })) > 0;
  }
}
