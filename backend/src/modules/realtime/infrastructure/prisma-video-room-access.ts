import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { canAccessVideo } from '../../videos/domain/visibility.js';
import type { VideoRoomAccess } from '../domain/video-room-guard.js';

// Lecture de la vidéo et de son propriétaire depuis le module realtime : acceptable dans un
// monolithe à base unique (ADR-003). La règle elle-même est celle du domaine vidéo (ADR-016).
export class PrismaVideoRoomAccess implements VideoRoomAccess {
  constructor(private readonly prisma: PrismaService) {}

  async canJoin(videoId: string, userId: string | null): Promise<boolean> {
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
