import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { canAccessVideo } from '../../videos/domain/visibility.js';
import { VIDEO_SELECT } from '../../videos/infrastructure/prisma-video.repository.js';
import type { PlaylistDetail, PlaylistSummary } from '../domain/playlist.js';
import type { PlaylistRepository, PlaylistVideos } from '../domain/ports.js';

// Une vidéo privée n'apparaît que pour son propriétaire (ADR-016) : filtre à la lecture, la
// ligne reste dans la playlist et réapparaît si la vidéo redevient publique.
function visibleTo(userId: string): Prisma.VideoWhereInput {
  return {
    OR: [{ visibility: { not: 'PRIVATE' } }, { channel: { ownerId: userId } }],
  };
}

export class PrismaPlaylistRepository implements PlaylistRepository {
  constructor(private readonly prisma: PrismaService) {}

  async listOwned(
    userId: string,
    videoId?: string,
  ): Promise<PlaylistSummary[]> {
    const visible = { video: visibleTo(userId) };
    const rows = await this.prisma.playlist.findMany({
      where: { userId },
      orderBy: [{ updatedAt: 'desc' }, { id: 'asc' }],
      select: {
        id: true,
        title: true,
        updatedAt: true,
        _count: { select: { items: { where: visible } } },
        items: {
          where: visible,
          orderBy: { addedAt: 'asc' },
          take: 1,
          select: { video: { select: { thumbnailUrl: true } } },
        },
      },
    });
    const containing = videoId
      ? new Set(
          (
            await this.prisma.playlistItem.findMany({
              where: { videoId, playlist: { userId } },
              select: { playlistId: true },
            })
          ).map((item) => item.playlistId),
        )
      : null;
    return rows.map((row) => ({
      id: row.id,
      title: row.title,
      videoCount: row._count.items,
      thumbnailUrl: row.items[0]?.video.thumbnailUrl ?? null,
      updatedAt: row.updatedAt,
      ...(containing ? { containsVideo: containing.has(row.id) } : {}),
    }));
  }

  countOwned(userId: string): Promise<number> {
    return this.prisma.playlist.count({ where: { userId } });
  }

  async create(userId: string, title: string): Promise<PlaylistSummary> {
    const playlist = await this.prisma.playlist.create({
      data: { userId, title },
      select: { id: true, title: true, updatedAt: true },
    });
    return { ...playlist, videoCount: 0, thumbnailUrl: null };
  }

  async findDetail(id: string, userId: string): Promise<PlaylistDetail | null> {
    const playlist = await this.prisma.playlist.findFirst({
      where: { id, userId },
      select: {
        id: true,
        title: true,
        updatedAt: true,
        items: {
          where: { video: visibleTo(userId) },
          orderBy: [{ addedAt: 'asc' }, { videoId: 'asc' }],
          select: { video: { select: VIDEO_SELECT } },
        },
      },
    });
    if (!playlist) {
      return null;
    }
    return {
      id: playlist.id,
      title: playlist.title,
      updatedAt: playlist.updatedAt,
      videos: playlist.items.map((item) => item.video),
    };
  }

  async countItems(id: string, userId: string): Promise<number | null> {
    const playlist = await this.prisma.playlist.findFirst({
      where: { id, userId },
      select: { _count: { select: { items: true } } },
    });
    return playlist ? playlist._count.items : null;
  }

  async rename(id: string, userId: string, title: string): Promise<boolean> {
    const { count } = await this.prisma.playlist.updateMany({
      where: { id, userId },
      data: { title },
    });
    return count > 0;
  }

  async delete(id: string, userId: string): Promise<boolean> {
    const { count } = await this.prisma.playlist.deleteMany({
      where: { id, userId },
    });
    return count > 0;
  }

  // Une modification du contenu fait remonter la playlist en tête de la liste.
  async addVideo(
    id: string,
    userId: string,
    videoId: string,
  ): Promise<boolean> {
    return this.prisma.$transaction(async (tx) => {
      const { count } = await tx.playlist.updateMany({
        where: { id, userId },
        data: { updatedAt: new Date() },
      });
      if (count === 0) return false;
      await tx.playlistItem.createMany({
        data: { playlistId: id, videoId },
        skipDuplicates: true,
      });
      return true;
    });
  }

  async removeVideo(
    id: string,
    userId: string,
    videoId: string,
  ): Promise<boolean> {
    return this.prisma.$transaction(async (tx) => {
      const { count } = await tx.playlist.updateMany({
        where: { id, userId },
        data: { updatedAt: new Date() },
      });
      if (count === 0) return false;
      await tx.playlistItem.deleteMany({ where: { playlistId: id, videoId } });
      return true;
    });
  }
}

export class PrismaPlaylistVideos implements PlaylistVideos {
  constructor(private readonly prisma: PrismaService) {}

  async isAccessible(videoId: string, userId: string): Promise<boolean> {
    const video = await this.prisma.video.findUnique({
      where: { id: videoId },
      select: { visibility: true, channel: { select: { ownerId: true } } },
    });
    return (
      video !== null &&
      canAccessVideo(video.visibility, video.channel.ownerId, userId)
    );
  }
}
