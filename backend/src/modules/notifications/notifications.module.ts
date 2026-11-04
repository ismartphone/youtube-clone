import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { ManageDeviceTokensUseCase } from './application/device-tokens.use-case.js';
import {
  BackgroundPublicationNotifier,
  NotifyPublicationUseCase,
} from './application/notify-publication.use-case.js';
import {
  DEVICE_TOKEN_REPOSITORY,
  PUBLICATION_REPOSITORY,
  PUSH_SENDER,
  type DeviceTokenRepository,
  type PublicationRepository,
  type PushSender,
} from './domain/ports.js';
import { PUBLICATION_NOTIFIER } from './domain/publication-notifier.js';
import { FirebasePushSender } from './infrastructure/firebase-push-sender.js';
import {
  PrismaDeviceTokenRepository,
  PrismaPublicationRepository,
} from './infrastructure/prisma-notification.repository.js';
import { DevicesController } from './interfaces/http/devices.controller.js';

// Notifications de publication par Firebase Cloud Messaging (ADR-020). Les autres modules n'en
// voient que le port PUBLICATION_NOTIFIER.
@Module({
  imports: [AuthModule],
  controllers: [DevicesController],
  providers: [
    {
      provide: PUSH_SENDER,
      useFactory: () =>
        new FirebasePushSender(process.env.FIREBASE_SERVICE_ACCOUNT),
    },
    {
      provide: DEVICE_TOKEN_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaDeviceTokenRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: PUBLICATION_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaPublicationRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: ManageDeviceTokensUseCase,
      useFactory: (tokens: DeviceTokenRepository) =>
        new ManageDeviceTokensUseCase(tokens),
      inject: [DEVICE_TOKEN_REPOSITORY],
    },
    {
      provide: NotifyPublicationUseCase,
      useFactory: (
        publications: PublicationRepository,
        tokens: DeviceTokenRepository,
        sender: PushSender,
      ) => new NotifyPublicationUseCase(publications, tokens, sender),
      inject: [PUBLICATION_REPOSITORY, DEVICE_TOKEN_REPOSITORY, PUSH_SENDER],
    },
    {
      provide: PUBLICATION_NOTIFIER,
      useFactory: (notifyPublication: NotifyPublicationUseCase) =>
        new BackgroundPublicationNotifier(notifyPublication),
      inject: [NotifyPublicationUseCase],
    },
  ],
  exports: [PUBLICATION_NOTIFIER],
})
export class NotificationsModule {}
