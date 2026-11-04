import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { ConversationRoomAccess } from '../domain/video-room-guard.js';

// Lecture des participants depuis le module realtime, comme pour les vidéos (ADR-003).
export class PrismaConversationRoomAccess implements ConversationRoomAccess {
  constructor(private readonly prisma: PrismaService) {}

  async isParticipant(
    conversationId: string,
    userId: string,
  ): Promise<boolean> {
    const count = await this.prisma.conversation.count({
      where: {
        id: conversationId,
        OR: [{ userAId: userId }, { userBId: userId }],
      },
    });
    return count > 0;
  }
}
