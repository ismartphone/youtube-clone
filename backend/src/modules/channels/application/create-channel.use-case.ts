import type { AvatarGenerator } from '../domain/avatar.js';
import {
  normalizeHandle,
  type ChannelSummary,
  type NewChannel,
} from '../domain/channel.js';
import type { ChannelRepository } from '../domain/ports.js';

// Une seule chaîne par utilisateur. Le rôle CREATOR n'apparaît dans l'access token
// qu'au prochain renouvellement : le rôle est relu en base à chaque refresh.
// La chaîne reçoit un avatar généré (initiales sur fond de couleur), nommé d'après son
// identifiant : il n'est produit qu'une fois la chaîne créée, pour qu'un handle refusé
// n'écrase jamais le fichier d'une autre chaîne. Un échec laisse la chaîne sans avatar.
export class CreateChannelUseCase {
  constructor(
    private readonly channels: ChannelRepository,
    private readonly avatars: AvatarGenerator,
  ) {}

  async execute(ownerId: string, channel: NewChannel): Promise<ChannelSummary> {
    const created = await this.channels.create(ownerId, {
      name: channel.name.trim(),
      handle: normalizeHandle(channel.handle),
      description: channel.description?.trim() || null,
    });
    const avatarUrl = await this.avatars.generate(created.name, created.id);
    if (!avatarUrl) {
      return created;
    }
    return (await this.channels.updateAvatar(created.id, avatarUrl)) ?? created;
  }
}

// Rattrapage : un avatar généré pour chaque chaîne qui n'en a pas (chaînes créées avant la
// génération automatique, données du seed). Renvoie le nombre d'avatars créés.
export class GenerateMissingAvatarsUseCase {
  constructor(
    private readonly channels: ChannelRepository,
    private readonly avatars: AvatarGenerator,
  ) {}

  async execute(): Promise<{ generated: number; failed: string[] }> {
    let generated = 0;
    const failed: string[] = [];
    for (const channel of await this.channels.findWithoutAvatar()) {
      const avatarUrl = await this.avatars.generate(channel.name, channel.id);
      if (
        avatarUrl &&
        (await this.channels.updateAvatar(channel.id, avatarUrl))
      ) {
        generated++;
      } else {
        failed.push(channel.name);
      }
    }
    return { generated, failed };
  }
}
