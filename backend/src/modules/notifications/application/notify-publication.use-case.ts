import { Logger } from '@nestjs/common';
import { levelsNotifiedFor, toMessage } from '../domain/notification.js';
import type {
  DeviceTokenRepository,
  PublicationRepository,
  PushSender,
} from '../domain/ports.js';
import type { PublicationNotifier } from '../domain/publication-notifier.js';

// Firebase accepte au plus 500 jetons par envoi groupé.
const MAX_TOKENS_PER_SEND = 500;

// Notification de publication (ADR-020) : aux abonnés dont la cloche couvre le format de la
// vidéo, créateur exclu, une seule fois par vidéo. Les jetons refusés par Firebase sont supprimés.
export class NotifyPublicationUseCase {
  constructor(
    private readonly publications: PublicationRepository,
    private readonly tokens: DeviceTokenRepository,
    private readonly sender: PushSender,
  ) {}

  // Renvoie le nombre d'appareils joints.
  async execute(videoId: string): Promise<number> {
    if (!this.sender.enabled) {
      return 0;
    }
    const publication = await this.publications.claim(videoId);
    if (!publication) {
      return 0;
    }
    const recipients = await this.publications.findRecipientTokens(
      publication.channelId,
      levelsNotifiedFor(publication.format),
      publication.ownerId,
    );
    const message = toMessage(publication);
    let delivered = 0;
    for (let i = 0; i < recipients.length; i += MAX_TOKENS_PER_SEND) {
      const batch = recipients.slice(i, i + MAX_TOKENS_PER_SEND);
      const rejected = await this.sender.send(batch, message);
      if (rejected.length > 0) {
        await this.tokens.removeAll(rejected);
      }
      delivered += batch.length - rejected.length;
    }
    return delivered;
  }
}

// Branché sur la publication par le module videos : l'envoi part après la réponse et un échec
// n'atteint jamais la publication, il est seulement journalisé.
export class BackgroundPublicationNotifier implements PublicationNotifier {
  private readonly logger = new Logger('PublicationNotifier');

  constructor(private readonly notifyPublication: NotifyPublicationUseCase) {}

  notify(videoId: string): void {
    setImmediate(() => {
      this.notifyPublication.execute(videoId).then(
        (delivered) => {
          if (delivered > 0) {
            this.logger.log(
              `Vidéo ${videoId} annoncée à ${delivered} appareil(s)`,
            );
          }
        },
        (error: unknown) =>
          this.logger.error(
            `Notification de la vidéo ${videoId} en échec : ${(error as Error).message}`,
          ),
      );
    });
  }
}
