import type { ViewerChannelStatus } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

export class GetFollowStatusUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  // Sans userId, c'est un visiteur : jamais abonné, jamais propriétaire.
  async execute(
    channelId: string,
    userId?: string,
  ): Promise<ViewerChannelStatus> {
    const [followerCount, isFollowing, ownerId] = await Promise.all([
      this.channels.countFollowers(channelId),
      userId ? this.channels.isFollowing(userId, channelId) : false,
      userId ? this.channels.findOwnerId(channelId) : null,
    ]);
    return {
      followerCount,
      isFollowing,
      isOwner: userId !== undefined && ownerId === userId,
    };
  }
}
