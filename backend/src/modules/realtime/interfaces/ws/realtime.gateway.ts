import { Inject } from '@nestjs/common';
import type { OnModuleDestroy } from '@nestjs/common';
import {
  ConnectedSocket,
  MessageBody,
  OnGatewayConnection,
  OnGatewayDisconnect,
  OnGatewayInit,
  SubscribeMessage,
  WebSocketGateway,
} from '@nestjs/websockets';
import type { IncomingMessage } from 'node:http';
import type { Server, WebSocket } from 'ws';
import {
  TOKEN_SERVICE,
  type TokenService,
} from '../../../auth/domain/ports.js';
import { LiveViewersService } from '../../application/live-viewers.service.js';
import { VideoRoomGuardService } from '../../application/video-room-guard.service.js';
import { Heartbeat } from '../../infrastructure/heartbeat.js';
import {
  conversationRoom,
  InMemoryRooms,
  liveRoom,
  userRoom,
  videoRoom,
} from '../../infrastructure/in-memory-rooms.js';

// Code de fermeture renvoyé quand le token du handshake est invalide ou expiré :
// le client le renouvelle, puis se reconnecte.
export const INVALID_TOKEN_CLOSE_CODE = 4401;

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Passerelle WebSocket (ADR-004), sur le même port que l'API : ws://hôte/realtime.
 *
 * Le JWT est transmis dans le handshake (en-tête Authorization: Bearer <token>).
 * Sans en-tête, le client est un visiteur. La connexion ne sert qu'à recevoir :
 * commentaires et « j'aime » sont envoyés par l'API REST, puis diffusés ici.
 *
 * Messages du client : { "event": "join" | "leave", "data": ... }, avec dans data une seule salle :
 * - { "videoId": "..." } : salle d'une vidéo. Celle d'une vidéo privée n'est ouverte qu'à son
 *   propriétaire (ADR-016) ;
 * - { "conversationId": "..." } : salle d'une conversation, pour ses deux participants (ADR-025) ;
 * - { "userId": "..." } : salle personnelle, pour l'utilisateur lui-même (liste des conversations) ;
 * - { "liveId": "..." } : salle d'un live en cours, ouverte à tous, visiteurs compris (ADR-029).
 * Un join refusé ne fait rien, exactement comme pour une salle qui n'existe pas.
 * Messages du serveur : { "event": "comment:new" | "comment:reply" | "views:update" |
 * "likes:update" | "message:new" | "live:message" | "live:viewers" | "live:ended", "data": ... }.
 *
 * Battement (ADR-029) : le serveur envoie un ping toutes les 30 secondes et coupe la connexion qui
 * n'a pas répondu au précédent, pour que le compteur de spectateurs d'un live reste vrai après une
 * coupure brutale. Une fermeture propre est vue aussitôt, sans attendre le battement.
 */
@WebSocketGateway({ path: '/realtime' })
export class RealtimeGateway
  implements
    OnGatewayInit,
    OnGatewayConnection,
    OnGatewayDisconnect,
    OnModuleDestroy
{
  constructor(
    @Inject(TOKEN_SERVICE) private readonly tokens: TokenService,
    private readonly rooms: InMemoryRooms,
    private readonly guard: VideoRoomGuardService,
    // Instancié ici pour être branché sur les salles (compteur de spectateurs des lives).
    private readonly viewers: LiveViewersService,
  ) {}

  private heartbeat: Heartbeat | null = null;

  afterInit(server: Server) {
    this.heartbeat = new Heartbeat(() => server.clients);
    this.heartbeat.start();
  }

  onModuleDestroy() {
    this.heartbeat?.stop();
    this.viewers.stop();
  }

  // Utilisateur authentifié au handshake ; absent pour un visiteur.
  private readonly users = new WeakMap<WebSocket, string>();

  async handleConnection(client: WebSocket, request: IncomingMessage) {
    client.on('pong', () => this.heartbeat?.alive(client));
    client.on('close', () => this.heartbeat?.forget(client));
    const header = request.headers.authorization;
    if (!header) {
      return; // visiteur, en lecture seule
    }
    const [scheme, token] = header.split(' ');
    const user =
      scheme === 'Bearer' && token
        ? await this.tokens.verifyAccessToken(token)
        : null;
    if (!user) {
      // Comme sur l'API : un token présent mais invalide est refusé, pas ignoré.
      client.close(INVALID_TOKEN_CLOSE_CODE, 'Access token invalide ou expiré');
      return;
    }
    this.users.set(client, user.id);
  }

  handleDisconnect(client: WebSocket) {
    this.rooms.leaveAll(client);
  }

  @SubscribeMessage('join')
  async join(
    @ConnectedSocket() client: WebSocket,
    @MessageBody() data: unknown,
  ) {
    const userId = this.users.get(client) ?? null;
    const room = readRoom(data);
    switch (room?.kind) {
      case 'video':
        return this.guard.join(client, room.id, userId);
      case 'conversation':
        return this.guard.joinConversation(client, room.id, userId);
      case 'user':
        return this.guard.joinUser(client, room.id, userId);
      case 'live':
        return this.guard.joinLive(client, room.id, userId);
    }
  }

  @SubscribeMessage('leave')
  leave(@ConnectedSocket() client: WebSocket, @MessageBody() data: unknown) {
    const room = readRoom(data);
    if (room) {
      this.rooms.leave(client, ROOM_KEYS[room.kind](room.id));
    }
  }
}

const ROOM_KEYS = {
  video: videoRoom,
  conversation: conversationRoom,
  user: userRoom,
  live: liveRoom,
} as const;

// Les messages viennent du réseau : on ne fait confiance à rien.
function readRoom(
  data: unknown,
): { kind: keyof typeof ROOM_KEYS; id: string } | null {
  const fields = (data ?? {}) as Record<string, unknown>;
  const kinds = [
    ['video', fields.videoId],
    ['conversation', fields.conversationId],
    ['user', fields.userId],
    ['live', fields.liveId],
  ] as const;
  for (const [kind, id] of kinds) {
    if (typeof id === 'string' && UUID_PATTERN.test(id)) {
      return { kind, id };
    }
  }
  return null;
}
