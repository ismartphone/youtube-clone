import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { LiveRoomAccess } from '../domain/video-room-guard.js';

// Lecture de l'état du live depuis le module realtime, comme pour les vidéos (ADR-003).
export class PrismaLiveRoomAccess implements LiveRoomAccess {
  constructor(private readonly prisma: PrismaService) {}

  async isRunning(liveId: string): Promise<boolean> {
    const count = await this.prisma.live.count({
      where: { id: liveId, status: 'LIVE' },
    });
    return count > 0;
  }
}
