import type { ChannelSummary, NewChannel } from './channel.js';

// Ports du module channels : les cas d'usage n'en connaissent que ces interfaces.

export const CHANNEL_REPOSITORY = Symbol('CHANNEL_REPOSITORY');

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
  isFollowing(userId: string, channelId: string): Promise<boolean>;
  // Chaîne dont l'utilisateur est propriétaire, ou null s'il n'en a pas.
  findOwnedBy(userId: string): Promise<ChannelSummary | null>;
  // Chaînes suivies, de l'abonnement le plus récent au plus ancien.
  findFollowedBy(userId: string): Promise<ChannelSummary[]>;
  // Toutes les chaînes, par nom (choix de la chaîne d'une vidéo dans le back-office).
  findAll(): Promise<ChannelSummary[]>;
}
