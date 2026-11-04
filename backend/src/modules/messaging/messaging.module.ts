import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import {
  MESSAGING_PUBLISHER,
  type MessagingPublisher,
} from '../realtime/domain/messaging-publisher.js';
import { RealtimeModule } from '../realtime/realtime.module.js';
import { MessagingUseCases } from './application/messaging.use-cases.js';
import {
  MESSAGING_REPOSITORY,
  type MessagingRepository,
} from './domain/ports.js';
import { PrismaMessagingRepository } from './infrastructure/prisma-messaging.repository.js';
import {
  ConversationController,
  MyConversationsController,
  UsersSearchController,
} from './interfaces/http/messaging.controller.js';

// Messagerie entre utilisateurs (ADR-025) : conversations à deux, diffusées par la passerelle
// temps réel existante.
@Module({
  imports: [AuthModule, RealtimeModule],
  controllers: [
    MyConversationsController,
    ConversationController,
    UsersSearchController,
  ],
  providers: [
    {
      provide: MESSAGING_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaMessagingRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: MessagingUseCases,
      useFactory: (
        repository: MessagingRepository,
        publisher: MessagingPublisher,
      ) => new MessagingUseCases(repository, publisher),
      inject: [MESSAGING_REPOSITORY, MESSAGING_PUBLISHER],
    },
  ],
})
export class MessagingModule {}
