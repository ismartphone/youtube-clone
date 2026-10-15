import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { Channel, Video } from '../domain/video.js';
import type {
  Page,
  Pagination,
  VideoFilter,
  VideoRepository,
} from '../domain/video.repository.js';

// Sélection explicite des champs : aucune donnée de l'utilisateur propriétaire
// (email, passwordHash, rôle) ne peut sortir de la base par ce dépôt.
const CHANNEL_SUMMARY_SELECT = {
  id: true,
  name: true,
  handle: true,
  avatarUrl: true,
} satisfies Prisma.ChannelSelect;

const VIDEO_SELECT = {
  id: true,
  title: true,
  description: true,
  videoUrl: true,
  thumbnailUrl: true,
  durationSeconds: true,
  format: true,
  isPremium: true,
  viewCount: true,
  publishedAt: true,
  channel: { select: CHANNEL_SUMMARY_SELECT },
  category: { select: { id: true, name: true } },
} satisfies Prisma.VideoSelect;

const NEWEST_FIRST = {
  publishedAt: 'desc',
} satisfies Prisma.VideoOrderByWithRelationInput;

export class PrismaVideoRepository implements VideoRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findPage(
    filter: VideoFilter,
    { page, pageSize }: Pagination,
  ): Promise<Page<Video>> {
    const where: Prisma.VideoWhereInput = {
      format: filter.format,
      categoryId: filter.categoryId,
      channel: filter.followedBy
        ? { followers: { some: { userId: filter.followedBy } } }
        : undefined,
    };
    const [items, total] = await this.prisma.$transaction([
      this.prisma.video.findMany({
        where,
        select: VIDEO_SELECT,
        orderBy: [NEWEST_FIRST, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.video.count({ where }),
    ]);
    return { items, page, pageSize, total };
  }

  findById(id: string): Promise<Video | null> {
    return this.prisma.video.findUnique({
      where: { id },
      select: VIDEO_SELECT,
    });
  }

  async exists(id: string): Promise<boolean> {
    const video = await this.prisma.video.findUnique({
      where: { id },
      select: { id: true },
    });
    return video !== null;
  }

  findChannelWithVideos(channelId: string): Promise<Channel | null> {
    return this.prisma.channel.findUnique({
      where: { id: channelId },
      select: {
        ...CHANNEL_SUMMARY_SELECT,
        description: true,
        createdAt: true,
        videos: {
          select: VIDEO_SELECT,
          orderBy: [NEWEST_FIRST, { id: 'asc' }],
        },
      },
    });
  }
}
