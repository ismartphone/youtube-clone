import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { ManagePlaylistsUseCase } from './application/playlist.use-cases.js';
import {
  PLAYLIST_REPOSITORY,
  PLAYLIST_VIDEOS,
  type PlaylistRepository,
  type PlaylistVideos,
} from './domain/ports.js';
import {
  PrismaPlaylistRepository,
  PrismaPlaylistVideos,
} from './infrastructure/prisma-playlist.repository.js';
import { PlaylistsController } from './interfaces/http/playlists.controller.js';

// Playlists privées des utilisateurs (ADR-019, partie 3). Module séparé de videos.
@Module({
  imports: [AuthModule],
  controllers: [PlaylistsController],
  providers: [
    {
      provide: PLAYLIST_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaPlaylistRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: PLAYLIST_VIDEOS,
      useFactory: (prisma: PrismaService) => new PrismaPlaylistVideos(prisma),
      inject: [PrismaService],
    },
    {
      provide: ManagePlaylistsUseCase,
      useFactory: (playlists: PlaylistRepository, videos: PlaylistVideos) =>
        new ManagePlaylistsUseCase(playlists, videos),
      inject: [PLAYLIST_REPOSITORY, PLAYLIST_VIDEOS],
    },
  ],
})
export class PlaylistsModule {}
