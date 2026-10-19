import type { ChannelSummary } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

// Back-office : pose, remplace ou efface (null) la bannière d'une chaîne.
export class UpdateChannelBannerUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  // Renvoie null si la chaîne n'existe pas.
  execute(channelId: string, bannerUrl: string | null): Promise<ChannelSummary | null> {
    return this.channels.updateBanner(channelId, bannerUrl);
  }
}
