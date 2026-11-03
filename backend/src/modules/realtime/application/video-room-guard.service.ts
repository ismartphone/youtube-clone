import type { WebSocket } from 'ws';
import type {
  VideoRoomAccess,
  VideoRoomGuard,
} from '../domain/video-room-guard.js';
import type { InMemoryVideoRooms } from '../infrastructure/in-memory-video-rooms.js';

// Applique la même règle d'accès à l'entrée dans une salle et aux membres déjà présents.
export class VideoRoomGuardService implements VideoRoomGuard {
  constructor(
    private readonly rooms: InMemoryVideoRooms,
    private readonly access: VideoRoomAccess,
  ) {}

  /**
   * Fait entrer le client dans la salle s'il peut voir la vidéo. Sinon rien ne se passe, comme
   * pour une vidéo qui n'existe pas : le refus ne révèle pas son existence (ADR-016).
   */
  async join(client: WebSocket, videoId: string, userId: string | null) {
    if (await this.access.canJoin(videoId, userId)) {
      this.rooms.join(client, videoId, userId);
    }
  }

  async revalidate(videoId: string): Promise<void> {
    for (const { client, userId } of this.rooms.members(videoId)) {
      if (!(await this.access.canJoin(videoId, userId))) {
        this.rooms.leave(client, videoId);
      }
    }
  }
}
