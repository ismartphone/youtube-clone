import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { VideoRoomGuardService } from './application/video-room-guard.service.js';
import {
  VIDEO_ROOM_ACCESS,
  VIDEO_ROOM_GUARD,
  type VideoRoomAccess,
} from './domain/video-room-guard.js';
import { VIDEO_ROOM_PUBLISHER } from './domain/video-room-publisher.js';
import { InMemoryVideoRooms } from './infrastructure/in-memory-video-rooms.js';
import { PrismaVideoRoomAccess } from './infrastructure/prisma-video-room-access.js';
import { RealtimeGateway } from './interfaces/ws/realtime.gateway.js';

// Temps réel (ADR-004). Les autres modules n'en voient que les ports VIDEO_ROOM_PUBLISHER
// (diffusion) et VIDEO_ROOM_GUARD (revérification des salles après un changement de visibilité).
@Module({
  imports: [AuthModule],
  providers: [
    InMemoryVideoRooms,
    { provide: VIDEO_ROOM_PUBLISHER, useExisting: InMemoryVideoRooms },
    {
      provide: VIDEO_ROOM_ACCESS,
      useFactory: (prisma: PrismaService) => new PrismaVideoRoomAccess(prisma),
      inject: [PrismaService],
    },
    {
      provide: VideoRoomGuardService,
      useFactory: (rooms: InMemoryVideoRooms, access: VideoRoomAccess) =>
        new VideoRoomGuardService(rooms, access),
      inject: [InMemoryVideoRooms, VIDEO_ROOM_ACCESS],
    },
    { provide: VIDEO_ROOM_GUARD, useExisting: VideoRoomGuardService },
    RealtimeGateway,
  ],
  exports: [VIDEO_ROOM_PUBLISHER, VIDEO_ROOM_GUARD],
})
export class RealtimeModule {}
