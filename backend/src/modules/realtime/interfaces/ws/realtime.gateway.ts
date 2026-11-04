import { Inject } from '@nestjs/common';
import {
  ConnectedSocket,
  MessageBody,
  OnGatewayConnection,
  OnGatewayDisconnect,
  SubscribeMessage,
  WebSocketGateway,
} from '@nestjs/websockets';
import type { IncomingMessage } from 'node:http';
import type { WebSocket } from 'ws';
import {
  TOKEN_SERVICE,
  type TokenService,
} from '../../../auth/domain/ports.js';
import { VideoRoomGuardService } from '../../application/video-room-guard.service.js';
import { InMemoryVideoRooms } from '../../infrastructure/in-memory-video-rooms.js';

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
 * Messages du client : { "event": "join" | "leave", "data": { "videoId": "..." } }.
 * La salle d'une vidéo privée n'est ouverte qu'à son propriétaire (ADR-016) : pour tout autre,
 * join ne fait rien, exactement comme pour une vidéo qui n'existe pas.
 * Messages du serveur : { "event": "comment:new" | "comment:reply" | "views:update" | "likes:update", "data": ... }.
 */
@WebSocketGateway({ path: '/realtime' })
export class RealtimeGateway
  implements OnGatewayConnection, OnGatewayDisconnect
{
  constructor(
    @Inject(TOKEN_SERVICE) private readonly tokens: TokenService,
    private readonly rooms: InMemoryVideoRooms,
    private readonly guard: VideoRoomGuardService,
  ) {}

  // Utilisateur authentifié au handshake ; absent pour un visiteur.
  private readonly users = new WeakMap<WebSocket, string>();

  async handleConnection(client: WebSocket, request: IncomingMessage) {
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
    const videoId = readVideoId(data);
    if (videoId) {
      await this.guard.join(client, videoId, this.users.get(client) ?? null);
    }
  }

  @SubscribeMessage('leave')
  leave(@ConnectedSocket() client: WebSocket, @MessageBody() data: unknown) {
    const videoId = readVideoId(data);
    if (videoId) {
      this.rooms.leave(client, videoId);
    }
  }
}

// Les messages viennent du réseau : on ne fait confiance à rien.
function readVideoId(data: unknown): string | null {
  const videoId = (data as { videoId?: unknown } | null)?.videoId;
  return typeof videoId === 'string' && UUID_PATTERN.test(videoId)
    ? videoId
    : null;
}
