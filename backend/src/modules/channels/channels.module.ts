import { Module } from '@nestjs/common';
import {
  ffmpegCommand,
  mediaDirectory,
} from '../../shared/infrastructure/media.config.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { VideosModule } from '../videos/videos.module.js';
import { CreateChannelUseCase } from './application/create-channel.use-case.js';
import { FollowChannelUseCase } from './application/follow-channel.use-case.js';
import { GetFollowStatusUseCase } from './application/get-follow-status.use-case.js';
import { GetOwnChannelUseCase } from './application/get-own-channel.use-case.js';
import { ListChannelsUseCase } from './application/list-channels.use-case.js';
import { ListSubscriptionsUseCase } from './application/list-subscriptions.use-case.js';
import { UpdateChannelBannerUseCase } from './application/update-channel-banner.use-case.js';
import { AVATAR_GENERATOR, type AvatarGenerator } from './domain/avatar.js';
import { CHANNEL_REPOSITORY, type ChannelRepository } from './domain/ports.js';
import { FfmpegAvatarGenerator } from './infrastructure/ffmpeg-avatar-generator.js';
import { PrismaChannelRepository } from './infrastructure/prisma-channel.repository.js';
import { AdminChannelsController } from './interfaces/http/admin-channels.controller.js';
import { ChannelsController } from './interfaces/http/channels.controller.js';
import { SubscriptionsController } from './interfaces/http/subscriptions.controller.js';

// Chaînes et abonnements gratuits (FOLLOW). L'offre payante (PREMIUM_SUBSCRIPTION) n'est pas ici.
const USE_CASES = [
  GetFollowStatusUseCase,
  FollowChannelUseCase,
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
    ...USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (channels: ChannelRepository) => new UseCase(channels),
      inject: [CHANNEL_REPOSITORY],
    })),
  ],
})
export class ChannelsModule {}
