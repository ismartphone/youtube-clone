import { WebSocket } from 'ws';
import type {
  MessagingEvents,
  MessagingPublisher,
} from '../domain/messaging-publisher.js';
import type {
  RealtimeEvents,
  VideoRoomPublisher,
} from '../domain/video-room-publisher.js';

// Clés de salle préfixées par type (ADR-025) : les salles des vidéos, des conversations et des
// utilisateurs ne se mélangent jamais.
export const videoRoom = (videoId: string) => `video:${videoId}`;
export const conversationRoom = (conversationId: string) =>
  `conversation:${conversationId}`;
export const userRoom = (userId: string) => `user:${userId}`;

// Salles gardées en mémoire : valable pour une seule instance du serveur.
// Avec plusieurs instances, il faudrait relayer les événements entre elles (Redis pub/sub).
export class InMemoryRooms implements VideoRoomPublisher, MessagingPublisher {
  private readonly rooms = new Map<string, Set<WebSocket>>();
  private readonly roomsByClient = new Map<WebSocket, Set<string>>();
  // Utilisateur de chaque client présent dans une salle (null pour un visiteur), pour revérifier
  // son accès quand la visibilité d'une vidéo change (ADR-016).
  private readonly users = new Map<WebSocket, string | null>();

  join(client: WebSocket, room: string, userId: string | null): void {
    this.users.set(client, userId);
    let members = this.rooms.get(room);
    if (!members) {
      members = new Set();
      this.rooms.set(room, members);
    }
    members.add(client);
    let joined = this.roomsByClient.get(client);
    if (!joined) {
      joined = new Set();
      this.roomsByClient.set(client, joined);
    }
    joined.add(room);
  }

  leave(client: WebSocket, room: string): void {
    const members = this.rooms.get(room);
    members?.delete(client);
    if (members?.size === 0) {
      this.rooms.delete(room);
    }
    this.roomsByClient.get(client)?.delete(room);
  }

  // À la déconnexion : le client quitte toutes ses salles.
  leaveAll(client: WebSocket): void {
    for (const room of this.roomsByClient.get(client) ?? []) {
      this.leave(client, room);
    }
    this.roomsByClient.delete(client);
    this.users.delete(client);
  }

  // Membres de la salle, avec leur utilisateur.
  members(room: string): { client: WebSocket; userId: string | null }[] {
    return [...(this.rooms.get(room) ?? [])].map((client) => ({
      client,
      userId: this.users.get(client) ?? null,
    }));
  }

  publish<E extends keyof RealtimeEvents>(
    videoId: string,
    event: E,
    data: RealtimeEvents[E],
  ): void {
    this.send(videoRoom(videoId), event, data);
  }

  toConversation<E extends keyof MessagingEvents>(
    conversationId: string,
    event: E,
    data: MessagingEvents[E],
  ): void {
    this.send(conversationRoom(conversationId), event, data);
  }

  toUser<E extends keyof MessagingEvents>(
    userId: string,
    event: E,
    data: MessagingEvents[E],
  ): void {
    this.send(userRoom(userId), event, data);
  }

  private send(room: string, event: string, data: unknown): void {
    const members = this.rooms.get(room);
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
