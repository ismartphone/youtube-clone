import { Module } from '@nestjs/common';
import type { Redis } from 'ioredis';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
} from '../../shared/infrastructure/media.config.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import {
  VIDEO_ROOM_GUARD,
  type VideoRoomGuard,
} from '../realtime/domain/video-room-guard.js';
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
import { ResolveViewerUseCase } from './application/resolve-viewer.use-case.js';
import { SearchUseCase } from './application/search.use-case.js';
import { RegenerateThumbnailUseCase } from './application/regenerate-thumbnail.use-case.js';
import { UpdateVideoUseCase } from './application/update-video.use-case.js';
import { GenerateHlsUseCase } from './application/generate-hls.use-case.js';
import { UploadVideoUseCase } from './application/upload-video.use-case.js';
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
  HLS_GENERATOR,
  HLS_QUEUE,
  type HlsGenerator,
  type HlsQueue,
} from './domain/hls.js';
import { PREMIUM_ACCESS, type PremiumAccess } from './domain/premium-access.js';
import {
  SEARCH_REPOSITORY,
  type SearchRepository,
} from './domain/search.repository.js';
import {
  LIKE_REPOSITORY,
  type LikeRepository,
} from './domain/like.repository.js';
import {
  VIDEO_REPOSITORY,
  type VideoRepository,
} from './domain/video.repository.js';
import {
  CHANNEL_OWNERSHIP,
  VIDEO_FILE_STORE,
  VIDEO_PROBER,
  type ChannelOwnership,
  type VideoFileStore,
  type VideoProber,
} from './domain/video-upload.js';
import {
  WATCH_HISTORY_REPOSITORY,
  type WatchHistoryRepository,
} from './domain/watch-history.repository.js';
import { FfmpegThumbnailGenerator } from './infrastructure/ffmpeg-thumbnail-generator.js';
import { FfmpegHlsGenerator } from './infrastructure/ffmpeg-hls-generator.js';
import { InProcessHlsQueue } from './infrastructure/in-process-hls-queue.js';
import { FfprobeVideoProber } from './infrastructure/ffprobe-video-prober.js';
import { LocalVideoFileStore } from './infrastructure/local-video-file-store.js';
import { PrismaChannelOwnership } from './infrastructure/prisma-channel-ownership.js';
import { PrismaCatalogStats } from './infrastructure/prisma-catalog-stats.js';
import { PrismaCategoryRepository } from './infrastructure/prisma-category.repository.js';
import { PrismaLikeRepository } from './infrastructure/prisma-like.repository.js';
import { PrismaPremiumAccess } from './infrastructure/prisma-premium-access.js';
import { PrismaSearchRepository } from './infrastructure/prisma-search.repository.js';
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
import { SearchController } from './interfaces/http/search.controller.js';
import { UploadController } from './interfaces/http/upload.controller.js';
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
    SearchController,
    CategoriesController,
    UploadController,
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
      provide: SEARCH_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaSearchRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: SearchUseCase,
      useFactory: (search: SearchRepository, counters: VideoCounters) =>
        new SearchUseCase(search, counters),
      inject: [SEARCH_REPOSITORY, VIDEO_COUNTERS],
    },
    {
      provide: PREMIUM_ACCESS,
      useFactory: (prisma: PrismaService) => new PrismaPremiumAccess(prisma),
      inject: [PrismaService],
    },
    {
      provide: ResolveViewerUseCase,
      useFactory: (premium: PremiumAccess) => new ResolveViewerUseCase(premium),
      inject: [PREMIUM_ACCESS],
    },
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
    // Échelle HLS (ADR-017) : générée hors ligne, une vidéo à la fois, en tâche de fond.
    {
      provide: HLS_GENERATOR,
      useFactory: () =>
        new FfmpegHlsGenerator({
          ffmpeg: ffmpegCommand(),
          ffprobe: ffprobeCommand(),
          mediaDir: mediaDirectory(),
        }),
    },
    {
      provide: GenerateHlsUseCase,
      useFactory: (
        videos: VideoRepository,
        catalog: VideoCatalog,
        hls: HlsGenerator,
      ) => new GenerateHlsUseCase(videos, catalog, hls),
      inject: [VIDEO_REPOSITORY, VIDEO_CATALOG, HLS_GENERATOR],
    },
    {
      provide: HLS_QUEUE,
      useFactory: (generateHls: GenerateHlsUseCase) =>
        new InProcessHlsQueue(generateHls),
      inject: [GenerateHlsUseCase],
    },
    {
      provide: CreateVideoUseCase,
      useFactory: (
        catalog: VideoCatalog,
        counters: VideoCounters,
        thumbnails: ThumbnailGenerator,
        hlsQueue: HlsQueue,
      ) => new CreateVideoUseCase(catalog, counters, thumbnails, hlsQueue),
      inject: [VIDEO_CATALOG, VIDEO_COUNTERS, THUMBNAIL_GENERATOR, HLS_QUEUE],
    },
    // Envoi depuis l'application mobile (ADR-015). ffprobe est cherché au démarrage ;
    // absent, seul l'envoi est refusé.
    {
      provide: VIDEO_PROBER,
      useFactory: () => new FfprobeVideoProber(ffprobeCommand()),
    },
    {
      provide: VIDEO_FILE_STORE,
      useFactory: () => new LocalVideoFileStore(mediaDirectory()),
    },
    {
      provide: CHANNEL_OWNERSHIP,
      useFactory: (prisma: PrismaService) => new PrismaChannelOwnership(prisma),
      inject: [PrismaService],
    },
    {
      provide: UploadVideoUseCase,
      useFactory: (
        ownership: ChannelOwnership,
        prober: VideoProber,
        files: VideoFileStore,
        createVideo: CreateVideoUseCase,
      ) => new UploadVideoUseCase(ownership, prober, files, createVideo),
      inject: [
        CHANNEL_OWNERSHIP,
        VIDEO_PROBER,
        VIDEO_FILE_STORE,
        CreateVideoUseCase,
      ],
    },
    {
      provide: UpdateVideoUseCase,
      useFactory: (
        videos: VideoRepository,
        catalog: VideoCatalog,
        counters: VideoCounters,
        thumbnails: ThumbnailGenerator,
        rooms: VideoRoomGuard,
        hls: HlsGenerator,
        hlsQueue: HlsQueue,
      ) =>
        new UpdateVideoUseCase(
          videos,
          catalog,
          counters,
          thumbnails,
          rooms,
          hls,
          hlsQueue,
        ),
      inject: [
        VIDEO_REPOSITORY,
        VIDEO_CATALOG,
        VIDEO_COUNTERS,
        THUMBNAIL_GENERATOR,
        VIDEO_ROOM_GUARD,
        HLS_GENERATOR,
        HLS_QUEUE,
      ],
    },
    {
      provide: RegenerateThumbnailUseCase,
      useFactory: (
        videos: VideoRepository,
        catalog: VideoCatalog,
        thumbnails: ThumbnailGenerator,
      ) => new RegenerateThumbnailUseCase(videos, catalog, thumbnails),
      inject: [VIDEO_REPOSITORY, VIDEO_CATALOG, THUMBNAIL_GENERATOR],
    },
    {
      provide: DeleteVideoUseCase,
      useFactory: (
        videos: VideoRepository,
        catalog: VideoCatalog,
        thumbnails: ThumbnailGenerator,
        hls: HlsGenerator,
        files: VideoFileStore,
      ) => new DeleteVideoUseCase(videos, catalog, thumbnails, hls, files),
      inject: [
        VIDEO_REPOSITORY,
        VIDEO_CATALOG,
        THUMBNAIL_GENERATOR,
        HLS_GENERATOR,
        VIDEO_FILE_STORE,
      ],
    },
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
