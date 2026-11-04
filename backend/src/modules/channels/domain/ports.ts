import type {
  ChannelChanges,
  ChannelImageKind,
  ChannelSummary,
  NewChannel,
  NotificationLevel,
  OwnChannel,
} from './channel.js';

// Ports du module channels : les cas d'usage n'en connaissent que ces interfaces.

export const CHANNEL_REPOSITORY = Symbol('CHANNEL_REPOSITORY');
export const CHANNEL_IMAGE_STORE = Symbol('CHANNEL_IMAGE_STORE');

export interface ChannelRepository {
  // Crée la chaîne et passe son propriétaire en rôle CREATOR, en une seule transaction.
  // Lève ChannelAlreadyOwnedError ou HandleAlreadyUsedError.
  create(ownerId: string, channel: NewChannel): Promise<ChannelSummary>;
  // Propriétaire de la chaîne, ou null si elle n'existe pas.
  findOwnerId(channelId: string): Promise<string | null>;
  // Idempotents : suivre deux fois, ou arrêter sans suivre, ne change rien.
  follow(userId: string, channelId: string): Promise<void>;
  unfollow(userId: string, channelId: string): Promise<void>;
  countFollowers(channelId: string): Promise<number>;
  // Cloche de l'abonnement, ou null si l'utilisateur ne suit pas la chaîne.
  findNotificationLevel(
    userId: string,
    channelId: string,
  ): Promise<NotificationLevel | null>;
  // Renvoie false si l'utilisateur ne suit pas la chaîne.
  setNotificationLevel(
    userId: string,
    channelId: string,
    level: NotificationLevel,
  ): Promise<boolean>;
  // Chaîne dont l'utilisateur est propriétaire, ou null s'il n'en a pas.
  findOwnedBy(userId: string): Promise<ChannelSummary | null>;
  // Même chaîne, avec sa description, pour la personnaliser (ADR-018).
  findOwnedDetails(userId: string): Promise<OwnChannel | null>;
  // Nom, handle, description et images. Lève HandleAlreadyUsedError ; null si la chaîne n'existe plus.
  update(
    channelId: string,
    changes: ChannelChanges & {
      avatarUrl?: string | null;
      bannerUrl?: string | null;
    },
  ): Promise<OwnChannel | null>;
  // Chaînes suivies, de l'abonnement le plus récent au plus ancien.
  findFollowedBy(userId: string): Promise<ChannelSummary[]>;
  // Toutes les chaînes, par nom (choix de la chaîne d'une vidéo dans le back-office).
  findAll(): Promise<ChannelSummary[]>;
  // Renvoie null si la chaîne n'existe pas.
  updateAvatar(
    channelId: string,
    avatarUrl: string,
  ): Promise<ChannelSummary | null>;
  // Chaînes sans avatar, à qui en générer un.
  findWithoutAvatar(): Promise<ChannelSummary[]>;
  // null efface la bannière. Renvoie null si la chaîne n'existe pas.
  updateBanner(
    channelId: string,
    bannerUrl: string | null,
  ): Promise<ChannelSummary | null>;
}

// Photo de profil et bannière envoyées par le créateur (ADR-018).
export interface ChannelImageStore {
  /**
   * Vérifie avec ffprobe que [path] est une image JPEG ou PNG, la recadre selon [kind] (photo
   * carrée 512×512, bannière 2048×569), la range sous un nom généré et renvoie son URL. Lève
   * InvalidChannelImageError, ou ChannelImageUnavailableError si FFmpeg est absent.
   */
  save(path: string, kind: ChannelImageKind): Promise<string>;
  // Vrai pour une image rangée par save ; faux pour un avatar généré ou une bannière du seed.
  isUploaded(url: string | null): boolean;
  // Supprime une image rangée par save ; sans effet pour toute autre URL.
  remove(url: string): Promise<void>;
}
