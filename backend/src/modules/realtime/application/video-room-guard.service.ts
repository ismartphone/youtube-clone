import type { WebSocket } from 'ws';
import type {
  ConversationRoomAccess,
  VideoRoomAccess,
  VideoRoomGuard,
} from '../domain/video-room-guard.js';
import {
  conversationRoom,
  userRoom,
  videoRoom,
  type InMemoryRooms,
} from '../infrastructure/in-memory-rooms.js';

// Applique les règles d'accès à l'entrée dans une salle et, pour les vidéos, aux membres déjà
// présents. Un refus ne fait rien, comme pour une salle qui n'existe pas : il ne révèle pas son
// existence (ADR-016, ADR-025).
export class VideoRoomGuardService implements VideoRoomGuard {
  constructor(
    private readonly rooms: InMemoryRooms,
    private readonly access: VideoRoomAccess,
    private readonly conversations: ConversationRoomAccess,
  ) {}

  // Salle d'une vidéo : ouverte à qui peut voir la vidéo.
  async join(client: WebSocket, videoId: string, userId: string | null) {
    if (await this.access.canJoin(videoId, userId)) {
      this.rooms.join(client, videoRoom(videoId), userId);
    }
  }

  // Salle d'une conversation : ses deux participants seulement.
  async joinConversation(
    client: WebSocket,
    conversationId: string,
    userId: string | null,
  ) {
    if (
      userId &&
      (await this.conversations.isParticipant(conversationId, userId))
    ) {
      this.rooms.join(client, conversationRoom(conversationId), userId);
    }
  }

  // Salle personnelle (liste des conversations) : l'utilisateur lui-même seulement.
  joinUser(client: WebSocket, roomUserId: string, userId: string | null) {
    if (userId && userId === roomUserId) {
      this.rooms.join(client, userRoom(roomUserId), userId);
    }
  }

  async revalidate(videoId: string): Promise<void> {
    for (const { client, userId } of this.rooms.members(videoRoom(videoId))) {
      if (!(await this.access.canJoin(videoId, userId))) {
        this.rooms.leave(client, videoRoom(videoId));
      }
    }
  }
}
