import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { LiveViewersService } from './application/live-viewers.service.js';
import { VideoRoomGuardService } from './application/video-room-guard.service.js';
import { LIVE_ROOMS } from './domain/live-rooms.js';
import { MESSAGING_PUBLISHER } from './domain/messaging-publisher.js';
import {
  CONVERSATION_ROOM_ACCESS,
  LIVE_ROOM_ACCESS,
  VIDEO_ROOM_ACCESS,
  VIDEO_ROOM_GUARD,
  type ConversationRoomAccess,
  type LiveRoomAccess,
  type VideoRoomAccess,
} from './domain/video-room-guard.js';
import { VIDEO_ROOM_PUBLISHER } from './domain/video-room-publisher.js';
import { InMemoryRooms } from './infrastructure/in-memory-rooms.js';
import { PrismaConversationRoomAccess } from './infrastructure/prisma-conversation-room-access.js';
import { PrismaLiveRoomAccess } from './infrastructure/prisma-live-room-access.js';
import { PrismaVideoRoomAccess } from './infrastructure/prisma-video-room-access.js';
import { RealtimeGateway } from './interfaces/ws/realtime.gateway.js';

// Temps réel (ADR-004). Les autres modules n'en voient que les ports VIDEO_ROOM_PUBLISHER,
// MESSAGING_PUBLISHER et LIVE_ROOMS (diffusion) et VIDEO_ROOM_GUARD (revérification des salles après un
// changement de visibilité).
@Module({
  imports: [AuthModule],
  providers: [
    InMemoryRooms,
    { provide: VIDEO_ROOM_PUBLISHER, useExisting: InMemoryRooms },
    { provide: MESSAGING_PUBLISHER, useExisting: InMemoryRooms },
    { provide: LIVE_ROOMS, useExisting: InMemoryRooms },
    {
      provide: LIVE_ROOM_ACCESS,
      useFactory: (prisma: PrismaService) => new PrismaLiveRoomAccess(prisma),
      inject: [PrismaService],
    },
    {
      provide: LiveViewersService,
      useFactory: (rooms: InMemoryRooms) => {
        const viewers = new LiveViewersService(rooms);
        rooms.onRoomChanged((room) => viewers.onRoomChanged(room));
        return viewers;
      },
      inject: [InMemoryRooms],
    },
    {
      provide: CONVERSATION_ROOM_ACCESS,
      useFactory: (prisma: PrismaService) =>
        new PrismaConversationRoomAccess(prisma),
      inject: [PrismaService],
    },
    {
      provide: VIDEO_ROOM_ACCESS,
      useFactory: (prisma: PrismaService) => new PrismaVideoRoomAccess(prisma),
      inject: [PrismaService],
    },
    {
      provide: VideoRoomGuardService,
      useFactory: (
        rooms: InMemoryRooms,
        access: VideoRoomAccess,
        conversations: ConversationRoomAccess,
        lives: LiveRoomAccess,
      ) => new VideoRoomGuardService(rooms, access, conversations, lives),
      inject: [
        InMemoryRooms,
        VIDEO_ROOM_ACCESS,
        CONVERSATION_ROOM_ACCESS,
        LIVE_ROOM_ACCESS,
      ],
    },
    { provide: VIDEO_ROOM_GUARD, useExisting: VideoRoomGuardService },
    RealtimeGateway,
  ],
  exports: [
    VIDEO_ROOM_PUBLISHER,
    MESSAGING_PUBLISHER,
    VIDEO_ROOM_GUARD,
    LIVE_ROOMS,
  ],
})
export class RealtimeModule {}
