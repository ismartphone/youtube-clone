import { Module } from '@nestjs/common';
import type { Redis } from 'ioredis';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import {
  ffmpegCommand,
  mediaDirectory,
} from '../../shared/infrastructure/media.config.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import {
  VIDEO_ROOM_PUBLISHER,
  type VideoRoomPublisher,
} from '../realtime/domain/video-room-publisher.js';
import { RealtimeModule } from '../realtime/realtime.module.js';
import { CreateVideoUseCase } from './application/create-video.use-case.js';
import { DeleteVideoUseCase } from './application/delete-video.use-case.js';
import { GetCatalogStatsUseCase } from './application/get-catalog-stats.use-case.js';
import { GetChannelUseCase } from './application/get-channel.use-case.js';
import { GetVideoUseCase } from './application/get-video.use-case.js';
import {
  GetLikeStatusUseCase,
  LikeVideoUseCase,
} from './application/like-video.use-case.js';
import {
  ListLikedVideosUseCase,
  ListWatchHistoryUseCase,
} from './application/list-personal-videos.use-case.js';
import { ListVideosUseCase } from './application/list-videos.use-case.js';
import {
  CreateCategoryUseCase,
  DeleteCategoryUseCase,
  ListCategoriesUseCase,
} from './application/manage-categories.use-case.js';
import { RecordViewUseCase } from './application/record-view.use-case.js';
import { RegenerateThumbnailUseCase } from './application/regenerate-thumbnail.use-case.js';
import { UpdateVideoUseCase } from './application/update-video.use-case.js';
import {
  CATALOG_STATS,
  CATEGORY_REPOSITORY,
  VIDEO_CATALOG,
  type CatalogStatsReader,
  type CategoryRepository,
  type VideoCatalog,
} from './domain/catalog.js';
import { VIDEO_COUNTERS, type VideoCounters } from './domain/counters.js';
import {
  THUMBNAIL_GENERATOR,
  type ThumbnailGenerator,
} from './domain/thumbnail-generator.js';
import {
  LIKE_REPOSITORY,
  type LikeRepository,
} from './domain/like.repository.js';
import {
  VIDEO_REPOSITORY,
  type VideoRepository,
} from './domain/video.repository.js';
import {
  WATCH_HISTORY_REPOSITORY,
  type WatchHistoryRepository,
} from './domain/watch-history.repository.js';
import { FfmpegThumbnailGenerator } from './infrastructure/ffmpeg-thumbnail-generator.js';
import { PrismaCatalogStats } from './infrastructure/prisma-catalog-stats.js';
import { PrismaCategoryRepository } from './infrastructure/prisma-category.repository.js';
import { PrismaLikeRepository } from './infrastructure/prisma-like.repository.js';
import { PrismaVideoCatalog } from './infrastructure/prisma-video-catalog.js';
import { PrismaVideoRepository } from './infrastructure/prisma-video.repository.js';
import { PrismaWatchHistoryRepository } from './infrastructure/prisma-watch-history.repository.js';
import { RedisBufferedCounter } from './infrastructure/redis-buffered-counter.js';
import {
  AdminCategoriesController,
  AdminStatsController,
  AdminVideosController,
} from './interfaces/http/admin.controllers.js';
import { CategoriesController } from './interfaces/http/categories.controller.js';
import { FeedController } from './interfaces/http/feed.controller.js';
import { VideosController } from './interfaces/http/videos.controller.js';

// Chaque compteur est un fournisseur à part : NestJS appelle ainsi ses hooks
// de démarrage (report périodique) et d'arrêt (dernier report avant de couper).
const VIEW_COUNTER = Symbol('VIEW_COUNTER');
const LIKE_COUNTER = Symbol('LIKE_COUNTER');

// Les cas d'usage ne dépendent que des ports du domaine : c'est ici qu'on branche les adaptateurs.
const READ_USE_CASES = [ListVideosUseCase, GetVideoUseCase, GetChannelUseCase];

const CATEGORY_USE_CASES = [
  ListCategoriesUseCase,
  CreateCategoryUseCase,
  DeleteCategoryUseCase,
];

@Module({
  imports: [AuthModule, RealtimeModule],
  controllers: [
    VideosController,
    FeedController,
    CategoriesController,
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
      provide: LIKE_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaLikeRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: VIEW_COUNTER,
      useFactory: (redis: Redis, prisma: PrismaService) =>
        new RedisBufferedCounter(redis, prisma, {
          name: 'views',
          column: 'viewCount',
        }),
      inject: [REDIS_CLIENT, PrismaService],
    },
    {
      provide: LIKE_COUNTER,
      useFactory: (redis: Redis, prisma: PrismaService) =>
        new RedisBufferedCounter(redis, prisma, {
          name: 'likes',
          column: 'likeCount',
        }),
      inject: [REDIS_CLIENT, PrismaService],
    },
    {
      provide: VIDEO_COUNTERS,
      useFactory: (
        views: RedisBufferedCounter,
        likes: RedisBufferedCounter,
      ): VideoCounters => ({ views, likes }),
      inject: [VIEW_COUNTER, LIKE_COUNTER],
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
      useFactory: (videos: VideoRepository, counters: VideoCounters) =>
        new UseCase(videos, counters),
      inject: [VIDEO_REPOSITORY, VIDEO_COUNTERS],
    })),
    {
      provide: WATCH_HISTORY_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaWatchHistoryRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: RecordViewUseCase,
      useFactory: (
        videos: VideoRepository,
        counters: VideoCounters,
        publisher: VideoRoomPublisher,
        history: WatchHistoryRepository,
      ) => new RecordViewUseCase(videos, counters, publisher, history),
      inject: [
        VIDEO_REPOSITORY,
        VIDEO_COUNTERS,
        VIDEO_ROOM_PUBLISHER,
        WATCH_HISTORY_REPOSITORY,
      ],
    },
    {
      provide: ListWatchHistoryUseCase,
      useFactory: (history: WatchHistoryRepository, counters: VideoCounters) =>
        new ListWatchHistoryUseCase(history, counters),
      inject: [WATCH_HISTORY_REPOSITORY, VIDEO_COUNTERS],
    },
    {
      provide: ListLikedVideosUseCase,
      useFactory: (likes: LikeRepository, counters: VideoCounters) =>
        new ListLikedVideosUseCase(likes, counters),
      inject: [LIKE_REPOSITORY, VIDEO_COUNTERS],
    },
    {
      provide: LikeVideoUseCase,
      useFactory: (
        videos: VideoRepository,
        likes: LikeRepository,
        counters: VideoCounters,
        publisher: VideoRoomPublisher,
      ) => new LikeVideoUseCase(videos, likes, counters, publisher),
      inject: [
        VIDEO_REPOSITORY,
        LIKE_REPOSITORY,
        VIDEO_COUNTERS,
        VIDEO_ROOM_PUBLISHER,
      ],
    },
    {
      provide: GetLikeStatusUseCase,
      useFactory: (likes: LikeRepository) => new GetLikeStatusUseCase(likes),
      inject: [LIKE_REPOSITORY],
    },
    {
      // FFmpeg est cherché au démarrage (onModuleInit) ; absent, seule la génération est refusée.
      provide: THUMBNAIL_GENERATOR,
      useFactory: () =>
        new FfmpegThumbnailGenerator({
          command: ffmpegCommand(),
          mediaDir: mediaDirectory(),
        }),
    },
    {
      provide: CreateVideoUseCase,
      useFactory: (
        catalog: VideoCatalog,
        counters: VideoCounters,
        thumbnails: ThumbnailGenerator,
      ) => new CreateVideoUseCase(catalog, counters, thumbnails),
      inject: [VIDEO_CATALOG, VIDEO_COUNTERS, THUMBNAIL_GENERATOR],
    },
    {
      provide: UpdateVideoUseCase,
      useFactory: (
        videos: VideoRepository,
        catalog: VideoCatalog,
        counters: VideoCounters,
        thumbnails: ThumbnailGenerator,
      ) => new UpdateVideoUseCase(videos, catalog, counters, thumbnails),
      inject: [
        VIDEO_REPOSITORY,
        VIDEO_CATALOG,
        VIDEO_COUNTERS,
        THUMBNAIL_GENERATOR,
      ],
    },
    ...[DeleteVideoUseCase, RegenerateThumbnailUseCase].map((UseCase) => ({
      provide: UseCase,
      useFactory: (
        videos: VideoRepository,
        catalog: VideoCatalog,
        thumbnails: ThumbnailGenerator,
      ) => new UseCase(videos, catalog, thumbnails),
      inject: [VIDEO_REPOSITORY, VIDEO_CATALOG, THUMBNAIL_GENERATOR],
    })),
    ...CATEGORY_USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (categories: CategoryRepository) => new UseCase(categories),
      inject: [CATEGORY_REPOSITORY],
    })),
    {
      provide: GetCatalogStatsUseCase,
      useFactory: (stats: CatalogStatsReader, counters: VideoCounters) =>
        new GetCatalogStatsUseCase(stats, counters),
      inject: [CATALOG_STATS, VIDEO_COUNTERS],
    },
  ],
  // La page de chaîne (module channels) affiche les vidéos de la chaîne.
  exports: [GetChannelUseCase],
})
export class VideosModule {}
