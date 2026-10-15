import {
  normalizeHandle,
  type ChannelSummary,
  type NewChannel,
} from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

// Une seule chaîne par utilisateur. Le rôle CREATOR n'apparaît dans l'access token
// qu'au prochain renouvellement : le rôle est relu en base à chaque refresh.
export class CreateChannelUseCase {
  constructor(private readonly channels: ChannelRepository) {}

  execute(ownerId: string, channel: NewChannel): Promise<ChannelSummary> {
    return this.channels.create(ownerId, {
      name: channel.name.trim(),
      handle: normalizeHandle(channel.handle),
    });
  }
}
