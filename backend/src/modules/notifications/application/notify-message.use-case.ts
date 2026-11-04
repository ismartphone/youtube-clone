import { Logger } from '@nestjs/common';
import type { MessageNotifier } from '../domain/message-notifier.js';
import type { MessagePush } from '../domain/notification.js';
import type {
  DeviceTokenRepository,
  MessagePushSender,
  MessageRecipientRepository,
} from '../domain/ports.js';

// Notification d'un message reçu (ADR-025, étape 3), sur le modèle de l'ADR-020 : aux appareils
// du destinataire, sauf s'il a coupé les notifications de messages. Elle part même si la
// conversation est ouverte : c'est l'application qui ne l'affiche pas (décision 8). Avec plusieurs
// comptes sur un appareil, son jeton n'appartient qu'au compte actif (ADR-022).
export class NotifyMessageUseCase {
  constructor(
    private readonly recipients: MessageRecipientRepository,
    private readonly tokens: DeviceTokenRepository,
    private readonly sender: MessagePushSender,
  ) {}

  // Renvoie le nombre d'appareils joints.
  async execute(recipientId: string, message: MessagePush): Promise<number> {
    if (!this.sender.enabled) {
      return 0;
    }
    const tokens = await this.recipients.findTokens(recipientId);
    const rejected = await this.sender.sendMessage(tokens, message);
    if (rejected.length > 0) {
      await this.tokens.removeAll(rejected);
    }
    return tokens.length - rejected.length;
  }
}

// Branché sur l'envoi d'un message par le module messaging : la notification part après la
// réponse et un échec n'atteint jamais l'envoi, il est seulement journalisé.
export class BackgroundMessageNotifier implements MessageNotifier {
  private readonly logger = new Logger('MessageNotifier');

  constructor(private readonly notifyMessage: NotifyMessageUseCase) {}

  notify(recipientId: string, message: MessagePush): void {
    setImmediate(() => {
      this.notifyMessage
        .execute(recipientId, message)
        .catch((error: unknown) =>
          this.logger.error(
            `Notification du message de la conversation ${message.conversationId} en échec : ${(error as Error).message}`,
          ),
        );
    });
  }
}
