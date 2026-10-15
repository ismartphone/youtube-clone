import { Module } from '@nestjs/common';
import type { Redis } from 'ioredis';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { GetChannelUseCase } from './application/get-channel.use-case.js';
import { GetVideoUseCase } from './application/get-video.use-case.js';
import { ListVideosUseCase } from './application/list-videos.use-case.js';
import { RecordViewUseCase } from './application/record-view.use-case.js';
import {
  VIDEO_REPOSITORY,
  type VideoRepository,
} from './domain/video.repository.js';
import { VIEW_COUNTER, type ViewCounter } from './domain/view-counter.js';
import { PrismaVideoRepository } from './infrastructure/prisma-video.repository.js';
import { RedisViewCounter } from './infrastructure/redis-view-counter.js';
import { ChannelsController } from './interfaces/http/channels.controller.js';
import { VideosController } from './interfaces/http/videos.controller.js';

// Les cas d'usage ne dépendent que des ports du domaine : c'est ici qu'on branche les adaptateurs.
const USE_CASES = [
  ListVideosUseCase,
  GetVideoUseCase,
  GetChannelUseCase,
  RecordViewUseCase,
];

@Module({
  controllers: [VideosController, ChannelsController],
  providers: [
    {
      provide: VIDEO_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaVideoRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: VIEW_COUNTER,
      useFactory: (redis: Redis, prisma: PrismaService) =>
        new RedisViewCounter(redis, prisma),
      inject: [REDIS_CLIENT, PrismaService],
    },
    ...USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (videos: VideoRepository, viewCounter: ViewCounter) =>
        new UseCase(videos, viewCounter),
      inject: [VIDEO_REPOSITORY, VIEW_COUNTER],
    })),
  ],
})
export class VideosModule {}
