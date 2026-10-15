import type { ChannelSummary } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

export class ListChannelsUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  execute(): Promise<ChannelSummary[]> {
    return this.channels.findAll();
  }
}
