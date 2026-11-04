import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Logger } from '@nestjs/common';
import { cert, initializeApp, type ServiceAccount } from 'firebase-admin/app';
import { getMessaging, type Messaging } from 'firebase-admin/messaging';
import type {
  MessagePush,
  PublicationMessage,
} from '../domain/notification.js';
import type { MessagePushSender, PushSender } from '../domain/ports.js';

// Jeton périmé ou appareil désinstallé : Firebase ne l'acceptera plus jamais.
const DEAD_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

// Envoi par Firebase Cloud Messaging (ADR-020). La clé de compte de service est lue dans le
// fichier désigné par FIREBASE_SERVICE_ACCOUNT, jamais versionné. Variable ou fichier absents :
// le serveur démarre quand même, sans notifications, comme le repli de Stripe (ADR-014).
export class FirebasePushSender implements PushSender, MessagePushSender {
  private readonly logger = new Logger('FirebasePushSender');
  private readonly messaging: Messaging | null;

  constructor(serviceAccountPath: string | undefined) {
    this.messaging = this.connect(serviceAccountPath);
  }

  get enabled(): boolean {
    return this.messaging !== null;
  }

  // Message de données seulement, en priorité haute pour réveiller l'application fermée :
  // elle construit la notification et l'ouvre sur la vidéo.
  send(tokens: string[], message: PublicationMessage): Promise<string[]> {
    return this.sendData(tokens, {
      type: 'video_published',
      videoId: message.videoId,
      channelName: message.channelName,
      title: message.title,
      format: message.format,
      thumbnailUrl: message.thumbnailUrl ?? '',
    });
  }

  // Message reçu (ADR-025) : l'application ouvre la conversation. Texte vide : vidéo partagée.
  sendMessage(tokens: string[], message: MessagePush): Promise<string[]> {
    return this.sendData(tokens, {
      type: 'message_received',
      conversationId: message.conversationId,
      senderName: message.senderName,
      text: message.text ?? '',
    });
  }

  private async sendData(
    tokens: string[],
    data: Record<string, string>,
  ): Promise<string[]> {
    if (!this.messaging || tokens.length === 0) {
      return [];
    }
    const response = await this.messaging.sendEachForMulticast({
      tokens,
      data,
      android: { priority: 'high' },
    });
    const rejected: string[] = [];
    response.responses.forEach((result, index) => {
      const code = result.error?.code;
      if (code && DEAD_TOKEN_CODES.has(code)) {
        rejected.push(tokens[index]);
      } else if (result.error) {
        this.logger.warn(`Envoi refusé par Firebase : ${result.error.message}`);
      }
    });
    return rejected;
  }

  private connect(serviceAccountPath: string | undefined): Messaging | null {
    if (!serviceAccountPath) {
      this.logger.log(
        'FIREBASE_SERVICE_ACCOUNT absente : notifications désactivées',
      );
      return null;
    }
    let account: ServiceAccount;
    try {
      account = JSON.parse(
        readFileSync(resolve(serviceAccountPath), 'utf8'),
      ) as ServiceAccount;
    } catch (error) {
      this.logger.warn(
        `Clé Firebase illisible (${serviceAccountPath}) : notifications désactivées. ${(error as Error).message}`,
      );
      return null;
    }
    const app = initializeApp({ credential: cert(account) }, 'notifications');
    this.logger.log('Notifications par Firebase Cloud Messaging');
    return getMessaging(app);
  }
}
