import { generatedAvatarKey, type AvatarGenerator } from '../domain/avatar.js';
import { ChannelNotFoundError } from '../domain/channel.errors.js';
import {
  normalizeHandle,
  type ChannelChanges,
  type ChannelImageKind,
  type OwnChannel,
} from '../domain/channel.js';
import type { ChannelImageStore, ChannelRepository } from '../domain/ports.js';

/**
 * Personnalisation de sa chaîne par le créateur (ADR-018) : nom, handle, description, photo de
 * profil et bannière. Toujours la chaîne de l'utilisateur connecté : personne ne modifie celle
 * d'un autre. Une image remplacée ou retirée est supprimée du stockage ; l'avatar généré
 * (initiales sur fond de couleur) est conservé, pour pouvoir y revenir.
 */
export class CustomizeChannelUseCase {
  constructor(
    private readonly channels: ChannelRepository,
    private readonly images: ChannelImageStore,
    private readonly avatars: AvatarGenerator,
  ) {}

  /** Lève ChannelNotFoundError sans chaîne, HandleAlreadyUsedError si le handle est pris. */
  async updateDetails(
    userId: string,
    changes: ChannelChanges,
  ): Promise<OwnChannel> {
    const channel = await this.ownChannel(userId);
    const name = changes.name?.trim();
    const update: ChannelChanges & { avatarUrl?: string } = {
      ...(name ? { name } : {}),
      ...(changes.handle ? { handle: normalizeHandle(changes.handle) } : {}),
      ...(changes.description !== undefined
        ? { description: changes.description?.trim() || null }
        : {}),
    };
    // Sans photo choisie, l'avatar affiche les initiales du nom : il suit le nouveau nom.
    if (
      name &&
      name !== channel.name &&
      !this.images.isUploaded(channel.avatarUrl)
    ) {
      const avatarUrl = await this.generatedAvatar(channel.id, name);
      if (avatarUrl) update.avatarUrl = avatarUrl;
    }
    return this.save(channel.id, update);
  }

  /** Photo ou bannière choisie dans la galerie : [path] est le fichier reçu, encore temporaire. */
  async setImage(
    userId: string,
    kind: ChannelImageKind,
    path: string,
  ): Promise<OwnChannel> {
    const channel = await this.ownChannel(userId);
    const url = await this.images.save(path, kind);
    let updated: OwnChannel;
    try {
      updated = await this.save(
        channel.id,
        kind === 'photo' ? { avatarUrl: url } : { bannerUrl: url },
      );
    } catch (error) {
      await this.images.remove(url);
      throw error;
    }
    const previous = kind === 'photo' ? channel.avatarUrl : channel.bannerUrl;
    if (previous && this.images.isUploaded(previous)) {
      await this.images.remove(previous);
    }
    return updated;
  }

  /**
   * Photo retirée : retour à l'avatar généré, d'après le nom actuel. Bannière retirée : la page
   * de chaîne n'en a plus. Une bannière posée par le back-office ou le seed est aussi retirée,
   * mais son fichier n'est pas supprimé.
   */
  async removeImage(
    userId: string,
    kind: ChannelImageKind,
  ): Promise<OwnChannel> {
    const channel = await this.ownChannel(userId);
    const previous = kind === 'photo' ? channel.avatarUrl : channel.bannerUrl;
    if (kind === 'photo' && !this.images.isUploaded(previous)) {
      return channel;
    }
    const updated = await this.save(
      channel.id,
      kind === 'photo'
        ? { avatarUrl: await this.generatedAvatar(channel.id, channel.name) }
        : { bannerUrl: null },
    );
    if (previous && this.images.isUploaded(previous)) {
      await this.images.remove(previous);
    }
    return updated;
  }

  private async ownChannel(userId: string): Promise<OwnChannel> {
    const channel = await this.channels.findOwnedDetails(userId);
    if (!channel) {
      throw new ChannelNotFoundError();
    }
    return channel;
  }

  private async save(
    channelId: string,
    changes: Parameters<ChannelRepository['update']>[1],
  ): Promise<OwnChannel> {
    const updated = await this.channels.update(channelId, changes);
    if (!updated) {
      throw new ChannelNotFoundError();
    }
    return updated;
  }

  // null si la génération échoue (journalisée). Après un changement de nom, la chaîne garde alors
  // son ancien avatar ; après le retrait de la photo, elle n'a plus d'avatar.
  private generatedAvatar(channelId: string, name: string) {
    return this.avatars.generate(name, generatedAvatarKey(channelId, name));
  }
}
