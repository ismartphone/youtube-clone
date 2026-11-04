import type { NotificationLevel } from '../../channels/domain/channel.js';
import type {
  MessagePush,
  Publication,
  PublicationMessage,
} from './notification.js';

// Ports du module notifications (ADR-020).

export const DEVICE_TOKEN_REPOSITORY = Symbol('DEVICE_TOKEN_REPOSITORY');
export const PUBLICATION_REPOSITORY = Symbol('PUBLICATION_REPOSITORY');
export const PUSH_SENDER = Symbol('PUSH_SENDER');
export const MESSAGE_PUSH_SENDER = Symbol('MESSAGE_PUSH_SENDER');
export const MESSAGE_RECIPIENT_REPOSITORY = Symbol(
  'MESSAGE_RECIPIENT_REPOSITORY',
);

export interface DeviceTokenRepository {
  // Rattache le jeton à l'utilisateur, même s'il appartenait à un autre compte sur cet appareil.
  save(userId: string, token: string): Promise<void>;
  // Sans effet si le jeton n'est pas à cet utilisateur.
  remove(userId: string, token: string): Promise<void>;
  // Jetons refusés par Firebase : appareils désinstallés ou jetons périmés.
  removeAll(tokens: string[]): Promise<void>;
}

export interface PublicationRepository {
  /**
   * Réserve l'annonce d'une vidéo publique jamais annoncée, en renseignant sa date d'envoi en une
   * seule écriture : deux appels simultanés n'annoncent pas deux fois. null si la vidéo n'existe
   * pas, n'est pas publique ou a déjà été annoncée.
   */
  claim(videoId: string): Promise<Publication | null>;
  // Jetons des abonnés de la chaîne dont la cloche est dans [levels], propriétaire exclu.
  findRecipientTokens(
    channelId: string,
    levels: NotificationLevel[],
    excludedUserId: string,
  ): Promise<string[]>;
}

export interface PushSender {
  // Faux sans clé de compte de service : rien n'est envoyé (repli, comme Stripe).
  readonly enabled: boolean;
  // Renvoie les jetons refusés définitivement par Firebase, à supprimer.
  send(tokens: string[], message: PublicationMessage): Promise<string[]>;
}

// Envoi d'un message reçu (ADR-025, étape 3), par le même compte de service Firebase.
export interface MessagePushSender {
  readonly enabled: boolean;
  // Renvoie les jetons refusés définitivement par Firebase, à supprimer.
  sendMessage(tokens: string[], message: MessagePush): Promise<string[]>;
}

// Destinataire d'un message et son réglage « Notifications de messages », stocké côté serveur.
export interface MessageRecipientRepository {
  // Jetons de ses appareils, ou aucun s'il a coupé les notifications de messages.
  findTokens(userId: string): Promise<string[]>;
  isEnabled(userId: string): Promise<boolean>;
  setEnabled(userId: string, enabled: boolean): Promise<void>;
}
