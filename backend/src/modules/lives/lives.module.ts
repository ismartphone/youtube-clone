import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { LIVE_ROOMS, type LiveRooms } from '../realtime/domain/live-rooms.js';
import { RealtimeModule } from '../realtime/realtime.module.js';
import { LivesUseCases } from './application/lives.use-cases.js';
import { LIVES_REPOSITORY, type LivesRepository } from './domain/ports.js';
import { PrismaLivesRepository } from './infrastructure/prisma-lives.repository.js';
import {
  LivesController,
  MyLivesController,
} from './interfaces/http/lives.controller.js';

// Live shopping (ADR-029) : direct simulé sur une vidéo du catalogue, avec son chat et son compteur
// de spectateurs, diffusés par la passerelle temps réel existante.
@Module({
  imports: [AuthModule, RealtimeModule],
  controllers: [MyLivesController, LivesController],
  providers: [
    {
      provide: LIVES_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaLivesRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: LivesUseCases,
      useFactory: (repository: LivesRepository, rooms: LiveRooms) =>
        new LivesUseCases(repository, rooms),
      inject: [LIVES_REPOSITORY, LIVE_ROOMS],
    },
  ],
})
export class LivesModule {}
