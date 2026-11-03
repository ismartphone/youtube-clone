import { WebSocket } from 'ws';
import type {
  RealtimeEvents,
  VideoRoomPublisher,
} from '../domain/video-room-publisher.js';

// Salles gardées en mémoire : valable pour une seule instance du serveur.
// Avec plusieurs instances, il faudrait relayer les événements entre elles (Redis pub/sub).
export class InMemoryVideoRooms implements VideoRoomPublisher {
  private readonly rooms = new Map<string, Set<WebSocket>>();
  private readonly roomsByClient = new Map<WebSocket, Set<string>>();
  // Utilisateur de chaque client présent dans une salle (null pour un visiteur), pour revérifier
  // son accès quand la visibilité d'une vidéo change (ADR-016).
  private readonly users = new Map<WebSocket, string | null>();

  join(client: WebSocket, videoId: string, userId: string | null): void {
    this.users.set(client, userId);
    let members = this.rooms.get(videoId);
    if (!members) {
      members = new Set();
      this.rooms.set(videoId, members);
    }
    members.add(client);
    let joined = this.roomsByClient.get(client);
    if (!joined) {
      joined = new Set();
      this.roomsByClient.set(client, joined);
    }
    joined.add(videoId);
  }

  leave(client: WebSocket, videoId: string): void {
    const members = this.rooms.get(videoId);
    members?.delete(client);
    if (members?.size === 0) {
      this.rooms.delete(videoId);
    }
    this.roomsByClient.get(client)?.delete(videoId);
  }

  // À la déconnexion : le client quitte toutes ses salles.
  leaveAll(client: WebSocket): void {
    for (const videoId of this.roomsByClient.get(client) ?? []) {
      this.leave(client, videoId);
    }
    this.roomsByClient.delete(client);
    this.users.delete(client);
  }

  // Membres de la salle, avec leur utilisateur.
  members(videoId: string): { client: WebSocket; userId: string | null }[] {
    return [...(this.rooms.get(videoId) ?? [])].map((client) => ({
      client,
      userId: this.users.get(client) ?? null,
    }));
  }

  publish<E extends keyof RealtimeEvents>(
    videoId: string,
    event: E,
    data: RealtimeEvents[E],
  ): void {
    const members = this.rooms.get(videoId);
    if (!members) {
      return;
    }
    // Même enveloppe { event, data } que les messages envoyés par le client.
    const message = JSON.stringify({ event, data });
    for (const client of members) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message);
      }
    }
  }
}
