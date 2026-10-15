import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module.js';
import { VIDEO_ROOM_PUBLISHER } from './domain/video-room-publisher.js';
import { InMemoryVideoRooms } from './infrastructure/in-memory-video-rooms.js';
import { RealtimeGateway } from './interfaces/ws/realtime.gateway.js';

// Temps réel (ADR-004). Les autres modules n'en voient que le port VIDEO_ROOM_PUBLISHER.
@Module({
  imports: [AuthModule],
  providers: [
    InMemoryVideoRooms,
    { provide: VIDEO_ROOM_PUBLISHER, useExisting: InMemoryVideoRooms },
    RealtimeGateway,
  ],
  exports: [VIDEO_ROOM_PUBLISHER],
})
export class RealtimeModule {}
