import type { NotificationLevel } from '../../channels/domain/channel.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { Publication } from '../domain/notification.js';
import type {
  DeviceTokenRepository,
  MessageRecipientRepository,
  PublicationRepository,
} from '../domain/ports.js';

export class PrismaDeviceTokenRepository implements DeviceTokenRepository {
  constructor(private readonly prisma: PrismaService) {}

  async save(userId: string, token: string): Promise<void> {
    await this.prisma.deviceToken.upsert({
      where: { token },
      create: { token, userId },
      update: { userId },
    });
  }

  async remove(userId: string, token: string): Promise<void> {
    await this.prisma.deviceToken.deleteMany({ where: { token, userId } });
  }

  async removeAll(tokens: string[]): Promise<void> {
    await this.prisma.deviceToken.deleteMany({
      where: { token: { in: tokens } },
    });
  }
}

export class PrismaPublicationRepository implements PublicationRepository {
  constructor(private readonly prisma: PrismaService) {}

  async claim(videoId: string): Promise<Publication | null> {
    // La condition et l'écriture tiennent en une requête : un second appel ne trouve plus rien.
    const { count } = await this.prisma.video.updateMany({
      where: { id: videoId, visibility: 'PUBLIC', notifiedAt: null },
      data: { notifiedAt: new Date() },
    });
    if (count === 0) {
      return null;
    }
    const video = await this.prisma.video.findUnique({
      where: { id: videoId },
      select: {
        title: true,
        format: true,
        thumbnailUrl: true,
        channel: { select: { id: true, name: true, ownerId: true } },
      },
    });
    if (!video) {
      return null;
    }
    return {
      videoId,
      title: video.title,
      format: video.format,
      thumbnailUrl: video.thumbnailUrl,
      channelId: video.channel.id,
      channelName: video.channel.name,
      ownerId: video.channel.ownerId,
    };
  }

  async findRecipientTokens(
    channelId: string,
    levels: NotificationLevel[],
    excludedUserId: string,
  ): Promise<string[]> {
    const devices = await this.prisma.deviceToken.findMany({
      where: {
        userId: { not: excludedUserId },
        user: {
          follows: {
            some: { channelId, notificationLevel: { in: levels } },
          },
        },
      },
      select: { token: true },
    });
    return devices.map((device) => device.token);
  }
}

export class PrismaMessageRecipientRepository implements MessageRecipientRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findTokens(userId: string): Promise<string[]> {
    const tokens = await this.prisma.deviceToken.findMany({
      where: { userId, user: { messageNotificationsEnabled: true } },
      select: { token: true },
    });
    return tokens.map(({ token }) => token);
  }

  async isEnabled(userId: string): Promise<boolean> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { messageNotificationsEnabled: true },
    });
    return user?.messageNotificationsEnabled ?? true;
  }

  async setEnabled(userId: string, enabled: boolean): Promise<void> {
    await this.prisma.user.update({
      where: { id: userId },
      data: { messageNotificationsEnabled: enabled },
    });
  }
}
