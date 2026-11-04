import { Module } from '@nestjs/common';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
} from '../../shared/infrastructure/media.config.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { VideosModule } from '../videos/videos.module.js';
import { CreateChannelUseCase } from './application/create-channel.use-case.js';
import { CustomizeChannelUseCase } from './application/customize-channel.use-case.js';
import { FollowChannelUseCase } from './application/follow-channel.use-case.js';
import { GetFollowStatusUseCase } from './application/get-follow-status.use-case.js';
import { GetOwnChannelUseCase } from './application/get-own-channel.use-case.js';
import { ListChannelsUseCase } from './application/list-channels.use-case.js';
import { ListSubscriptionsUseCase } from './application/list-subscriptions.use-case.js';
import { SetNotificationLevelUseCase } from './application/set-notification-level.use-case.js';
import { UpdateChannelBannerUseCase } from './application/update-channel-banner.use-case.js';
import { AVATAR_GENERATOR, type AvatarGenerator } from './domain/avatar.js';
import {
  CHANNEL_IMAGE_STORE,
  CHANNEL_REPOSITORY,
  type ChannelImageStore,
  type ChannelRepository,
} from './domain/ports.js';
import { FfmpegAvatarGenerator } from './infrastructure/ffmpeg-avatar-generator.js';
import { LocalChannelImageStore } from './infrastructure/local-channel-image-store.js';
import { PrismaChannelRepository } from './infrastructure/prisma-channel.repository.js';
import { AdminChannelsController } from './interfaces/http/admin-channels.controller.js';
import { ChannelsController } from './interfaces/http/channels.controller.js';
import { MyChannelController } from './interfaces/http/my-channel.controller.js';
import { SubscriptionsController } from './interfaces/http/subscriptions.controller.js';

// Chaînes et abonnements gratuits (FOLLOW). L'offre payante (PREMIUM_SUBSCRIPTION) n'est pas ici.
const USE_CASES = [
  GetFollowStatusUseCase,
  FollowChannelUseCase,
  SetNotificationLevelUseCase,
  ListSubscriptionsUseCase,
  ListChannelsUseCase,
  GetOwnChannelUseCase,
  UpdateChannelBannerUseCase,
];

@Module({
  imports: [AuthModule, VideosModule],
  controllers: [
    ChannelsController,
    SubscriptionsController,
    AdminChannelsController,
    MyChannelController,
  ],
  providers: [
    {
      provide: CHANNEL_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaChannelRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: AVATAR_GENERATOR,
      useFactory: () =>
        new FfmpegAvatarGenerator({
          command: ffmpegCommand(),
          mediaDir: mediaDirectory(),
        }),
    },
    {
      provide: CreateChannelUseCase,
      useFactory: (channels: ChannelRepository, avatars: AvatarGenerator) =>
        new CreateChannelUseCase(channels, avatars),
      inject: [CHANNEL_REPOSITORY, AVATAR_GENERATOR],
    },
    // Photo et bannière choisies par le créateur (ADR-018). ffprobe est cherché au démarrage ;
    // absent, seul l'envoi d'images est refusé.
    {
      provide: CHANNEL_IMAGE_STORE,
      useFactory: () =>
        new LocalChannelImageStore({
          ffmpeg: ffmpegCommand(),
          ffprobe: ffprobeCommand(),
          mediaDir: mediaDirectory(),
        }),
    },
    {
      provide: CustomizeChannelUseCase,
      useFactory: (
        channels: ChannelRepository,
        images: ChannelImageStore,
        avatars: AvatarGenerator,
      ) => new CustomizeChannelUseCase(channels, images, avatars),
      inject: [CHANNEL_REPOSITORY, CHANNEL_IMAGE_STORE, AVATAR_GENERATOR],
    },
    ...USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (channels: ChannelRepository) => new UseCase(channels),
      inject: [CHANNEL_REPOSITORY],
    })),
  ],
})
export class ChannelsModule {}
