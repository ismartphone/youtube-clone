import { NotFollowingError } from '../domain/channel.errors.js';
import type { NotificationLevel } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

// Règle la cloche d'un abonnement (ADR-020). Lève NotFollowingError sans abonnement.
export class SetNotificationLevelUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  async execute(
    userId: string,
    channelId: string,
    level: NotificationLevel,
  ): Promise<NotificationLevel> {
    if (!(await this.channels.setNotificationLevel(userId, channelId, level))) {
      throw new NotFollowingError();
    }
    return level;
  }
}
