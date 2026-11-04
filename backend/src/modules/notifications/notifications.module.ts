import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { ManageDeviceTokensUseCase } from './application/device-tokens.use-case.js';
import {
  BackgroundMessageNotifier,
  NotifyMessageUseCase,
} from './application/notify-message.use-case.js';
import {
  BackgroundPublicationNotifier,
  NotifyPublicationUseCase,
} from './application/notify-publication.use-case.js';
import {
  DEVICE_TOKEN_REPOSITORY,
  MESSAGE_PUSH_SENDER,
  MESSAGE_RECIPIENT_REPOSITORY,
  PUBLICATION_REPOSITORY,
  PUSH_SENDER,
  type DeviceTokenRepository,
  type MessagePushSender,
  type MessageRecipientRepository,
  type PublicationRepository,
  type PushSender,
} from './domain/ports.js';
import { MESSAGE_NOTIFIER } from './domain/message-notifier.js';
import { PUBLICATION_NOTIFIER } from './domain/publication-notifier.js';
import { FirebasePushSender } from './infrastructure/firebase-push-sender.js';
import {
  PrismaDeviceTokenRepository,
  PrismaMessageRecipientRepository,
  PrismaPublicationRepository,
} from './infrastructure/prisma-notification.repository.js';
import { DevicesController } from './interfaces/http/devices.controller.js';
import { NotificationSettingsController } from './interfaces/http/notification-settings.controller.js';

// Notifications par Firebase Cloud Messaging : publications (ADR-020) et messages reçus (ADR-025).
// Les autres modules n'en voient que les ports PUBLICATION_NOTIFIER et MESSAGE_NOTIFIER.
@Module({
  imports: [AuthModule],
  controllers: [DevicesController, NotificationSettingsController],
  providers: [
    {
      provide: PUSH_SENDER,
      useFactory: () =>
        new FirebasePushSender(process.env.FIREBASE_SERVICE_ACCOUNT),
    },
    // Même expéditeur Firebase, vu par son port des messages.
    { provide: MESSAGE_PUSH_SENDER, useExisting: PUSH_SENDER },
    {
      provide: MESSAGE_RECIPIENT_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaMessageRecipientRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: NotifyMessageUseCase,
      useFactory: (
        recipients: MessageRecipientRepository,
        tokens: DeviceTokenRepository,
        sender: MessagePushSender,
      ) => new NotifyMessageUseCase(recipients, tokens, sender),
      inject: [
        MESSAGE_RECIPIENT_REPOSITORY,
        DEVICE_TOKEN_REPOSITORY,
        MESSAGE_PUSH_SENDER,
      ],
    },
    {
      provide: MESSAGE_NOTIFIER,
      useFactory: (notifyMessage: NotifyMessageUseCase) =>
        new BackgroundMessageNotifier(notifyMessage),
      inject: [NotifyMessageUseCase],
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
  exports: [PUBLICATION_NOTIFIER, MESSAGE_NOTIFIER],
})
export class NotificationsModule {}
