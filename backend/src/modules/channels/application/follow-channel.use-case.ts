import {
  CannotFollowOwnChannelError,
  ChannelNotFoundError,
} from '../domain/channel.errors.js';
import type { FollowStatus } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

// S'abonner (follow = true) ou se désabonner (follow = false) d'une chaîne.
// Renvoie le nouvel état, pour que le client mette à jour son bouton et son compteur.
export class FollowChannelUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  async execute(
    userId: string,
    channelId: string,
    follow: boolean,
  ): Promise<FollowStatus> {
    const ownerId = await this.channels.findOwnerId(channelId);
    if (!ownerId) {
      throw new ChannelNotFoundError();
    }
    if (follow) {
      if (ownerId === userId) {
        throw new CannotFollowOwnChannelError();
      }
      await this.channels.follow(userId, channelId);
    } else {
      await this.channels.unfollow(userId, channelId);
    }
    // Un nouvel abonnement prend la cloche par défaut, Personnalisées (ADR-020).
    const [followerCount, notificationLevel] = await Promise.all([
      this.channels.countFollowers(channelId),
      follow ? this.channels.findNotificationLevel(userId, channelId) : null,
    ]);
    return { followerCount, isFollowing: follow, notificationLevel };
  }
}
