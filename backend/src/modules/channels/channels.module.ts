import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { VideosModule } from '../videos/videos.module.js';
import { CreateChannelUseCase } from './application/create-channel.use-case.js';
import { FollowChannelUseCase } from './application/follow-channel.use-case.js';
import { GetFollowStatusUseCase } from './application/get-follow-status.use-case.js';
import { ListChannelsUseCase } from './application/list-channels.use-case.js';
import { ListSubscriptionsUseCase } from './application/list-subscriptions.use-case.js';
import { CHANNEL_REPOSITORY, type ChannelRepository } from './domain/ports.js';
import { PrismaChannelRepository } from './infrastructure/prisma-channel.repository.js';
import { AdminChannelsController } from './interfaces/http/admin-channels.controller.js';
import { ChannelsController } from './interfaces/http/channels.controller.js';
import { SubscriptionsController } from './interfaces/http/subscriptions.controller.js';

// Chaînes et abonnements gratuits (FOLLOW). L'offre payante (PREMIUM_SUBSCRIPTION) n'est pas ici.
const USE_CASES = [
  CreateChannelUseCase,
  GetFollowStatusUseCase,
  FollowChannelUseCase,
  ListSubscriptionsUseCase,
  ListChannelsUseCase,
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
    ...USE_CASES.map((UseCase) => ({
      provide: UseCase,
      useFactory: (channels: ChannelRepository) => new UseCase(channels),
      inject: [CHANNEL_REPOSITORY],
    })),
  ],
})
export class ChannelsModule {}
