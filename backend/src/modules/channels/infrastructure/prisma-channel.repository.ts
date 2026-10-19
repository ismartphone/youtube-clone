import { Prisma, Role } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import {
  ChannelAlreadyOwnedError,
  HandleAlreadyUsedError,
} from '../domain/channel.errors.js';
import type { ChannelSummary, NewChannel } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

// Sélection explicite : aucune donnée du propriétaire (email, rôle) ne sort par ce dépôt.
const CHANNEL_SUMMARY_SELECT = {
  id: true,
  name: true,
  handle: true,
  avatarUrl: true,
} satisfies Prisma.ChannelSelect;

// Code Prisma d'une violation de contrainte d'unicité.
const UNIQUE_CONSTRAINT_VIOLATION = 'P2002';

export class PrismaChannelRepository implements ChannelRepository {
  constructor(private readonly prisma: PrismaService) {}

  async create(ownerId: string, channel: NewChannel): Promise<ChannelSummary> {
    try {
      return await this.prisma.$transaction(async (tx) => {
        const owned = await tx.channel.findUnique({
          where: { ownerId },
          select: { id: true },
        });
        if (owned) {
          throw new ChannelAlreadyOwnedError();
        }
        const created = await tx.channel.create({
          data: { ...channel, ownerId },
          select: CHANNEL_SUMMARY_SELECT,
        });
        // Seul un USER devient CREATOR : un ADMIN garde son rôle.
        await tx.user.updateMany({
          where: { id: ownerId, role: Role.USER },
          data: { role: Role.CREATOR },
        });
        return created;
      });
    } catch (error) {
      // Le propriétaire vient d'être vérifié : la contrainte violée est celle du handle.
      // Seules deux créations simultanées du même utilisateur feraient exception, cas négligé.
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === UNIQUE_CONSTRAINT_VIOLATION
      ) {
        throw new HandleAlreadyUsedError();
      }
      throw error;
    }
  }

  async findOwnerId(channelId: string): Promise<string | null> {
    const channel = await this.prisma.channel.findUnique({
      where: { id: channelId },
      select: { ownerId: true },
    });
    return channel?.ownerId ?? null;
  }

  async follow(userId: string, channelId: string): Promise<void> {
    await this.prisma.follow.createMany({
      data: { userId, channelId },
      skipDuplicates: true,
    });
  }

  async unfollow(userId: string, channelId: string): Promise<void> {
    await this.prisma.follow.deleteMany({ where: { userId, channelId } });
  }

  countFollowers(channelId: string): Promise<number> {
    return this.prisma.follow.count({ where: { channelId } });
  }

  async isFollowing(userId: string, channelId: string): Promise<boolean> {
    const follow = await this.prisma.follow.findUnique({
      where: { userId_channelId: { userId, channelId } },
      select: { userId: true },
    });
    return follow !== null;
  }

  findOwnedBy(userId: string): Promise<ChannelSummary | null> {
    return this.prisma.channel.findUnique({
      where: { ownerId: userId },
      select: CHANNEL_SUMMARY_SELECT,
    });
  }

  async findFollowedBy(userId: string): Promise<ChannelSummary[]> {
    const follows = await this.prisma.follow.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      select: { channel: { select: CHANNEL_SUMMARY_SELECT } },
    });
    return follows.map((follow) => follow.channel);
  }

  findAll(): Promise<ChannelSummary[]> {
    return this.prisma.channel.findMany({
      orderBy: { name: 'asc' },
      select: CHANNEL_SUMMARY_SELECT,
    });
  }
}
