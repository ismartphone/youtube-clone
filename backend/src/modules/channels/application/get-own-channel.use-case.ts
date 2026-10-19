import type { ChannelSummary } from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

// Chaîne de l'utilisateur connecté, pour l'onglet « Vous » de l'application.
export class GetOwnChannelUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  execute(userId: string): Promise<ChannelSummary | null> {
    return this.channels.findOwnedBy(userId);
  }
}
