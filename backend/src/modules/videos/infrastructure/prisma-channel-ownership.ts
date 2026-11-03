import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { ChannelOwnership } from '../domain/video-upload.js';

// Lecture de la table des chaînes depuis le module videos : acceptable dans un monolithe
// à base unique (ADR-003), comme les statistiques du catalogue.
export class PrismaChannelOwnership implements ChannelOwnership {
  constructor(private readonly prisma: PrismaService) {}

  async channelIdOf(userId: string): Promise<string | null> {
    const channel = await this.prisma.channel.findUnique({
      where: { ownerId: userId },
      select: { id: true },
    });
    return channel?.id ?? null;
  }
}
