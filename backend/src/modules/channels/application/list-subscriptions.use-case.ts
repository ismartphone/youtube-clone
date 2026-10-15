import type { ChannelSummary } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

export class ListSubscriptionsUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  execute(userId: string): Promise<ChannelSummary[]> {
    return this.channels.findFollowedBy(userId);
  }
}
