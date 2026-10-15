import { Module } from '@nestjs/common';
import type { Redis } from 'ioredis';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { CreateVideoUseCase } from './application/create-video.use-case.js';
import { DeleteVideoUseCase } from './application/delete-video.use-case.js';
import { GetCatalogStatsUseCase } from './application/get-catalog-stats.use-case.js';
import { GetChannelUseCase } from './application/get-channel.use-case.js';
import { GetVideoUseCase } from './application/get-video.use-case.js';
import { ListVideosUseCase } from './application/list-videos.use-case.js';
import {
  CreateCategoryUseCase,
  DeleteCategoryUseCase,
  ListCategoriesUseCase,
} from './application/manage-categories.use-case.js';
import { RecordViewUseCase } from './application/record-view.use-case.js';
import { UpdateVideoUseCase } from './application/update-video.use-case.js';
import {
  CATALOG_STATS,
  CATEGORY_REPOSITORY,
  VIDEO_CATALOG,
  type CatalogStatsReader,
  type CategoryRepository,
  type VideoCatalog,
} from './domain/catalog.js';
import {
  VIDEO_REPOSITORY,
  type VideoRepository,
} from './domain/video.repository.js';
import { VIEW_COUNTER, type ViewCounter } from './domain/view-counter.js';
import { PrismaCatalogStats } from './infrastructure/prisma-catalog-stats.js';
import { PrismaCategoryRepository } from './infrastructure/prisma-category.repository.js';
import { PrismaVideoCatalog } from './infrastructure/prisma-video-catalog.js';
import { PrismaVideoRepository } from './infrastructure/prisma-video.repository.js';
import { RedisViewCounter } from './infrastructure/redis-view-counter.js';
import {
  AdminCategoriesController,
  AdminStatsController,
  AdminVideosController,
} from './interfaces/http/admin.controllers.js';
import { FeedController } from './interfaces/http/feed.controller.js';
import { VideosController } from './interfaces/http/videos.controller.js';

// Les cas d'usage ne dépendent que des ports du domaine : c'est ici qu'on branche les adaptateurs.
const READ_USE_CASES = [
  ListVideosUseCase,
  GetVideoUseCase,
  GetChannelUseCase,
  RecordViewUseCase,
];

const CATALOG_USE_CASES = [CreateVideoUseCase, UpdateVideoUseCase];

const CATEGORY_USE_CASES = [
  ListCategoriesUseCase,
  CreateCategoryUseCase,
  DeleteCategoryUseCase,
];

@Module({
  imports: [AuthModule],
  controllers: [
    VideosController,
    FeedController,
    AdminVideosController,
    AdminCategoriesController,
    AdminStatsController,
  ],
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
    {
      provide: VIDEO_CATALOG,
      useFactory: (prisma: PrismaService) => new PrismaVideoCatalog(prisma),
      inject: [PrismaService],
    },
    {
      provide: CATEGORY_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaCategoryRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: CATALOG_STATS,
      useFactory: (prisma: PrismaService) => new PrismaCatalogStats(prisma),
      inject: [PrismaService],
    },
    ...READ_USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (videos: VideoRepository, viewCounter: ViewCounter) =>
        new UseCase(videos, viewCounter),
      inject: [VIDEO_REPOSITORY, VIEW_COUNTER],
    })),
    ...CATALOG_USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (catalog: VideoCatalog, viewCounter: ViewCounter) =>
        new UseCase(catalog, viewCounter),
      inject: [VIDEO_CATALOG, VIEW_COUNTER],
    })),
    {
      provide: DeleteVideoUseCase,
      useFactory: (catalog: VideoCatalog) => new DeleteVideoUseCase(catalog),
      inject: [VIDEO_CATALOG],
    },
    ...CATEGORY_USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (categories: CategoryRepository) => new UseCase(categories),
      inject: [CATEGORY_REPOSITORY],
    })),
    {
      provide: GetCatalogStatsUseCase,
      useFactory: (stats: CatalogStatsReader, viewCounter: ViewCounter) =>
        new GetCatalogStatsUseCase(stats, viewCounter),
      inject: [CATALOG_STATS, VIEW_COUNTER],
    },
  ],
  // La page de chaîne (module channels) affiche les vidéos de la chaîne.
  exports: [GetChannelUseCase],
})
export class VideosModule {}
