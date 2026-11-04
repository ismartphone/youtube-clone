import type { ChannelImageStore } from '../../channels/domain/ports.js';
import type { AdLogoStore } from '../domain/ports.js';

// Logo d'annonceur rangé par le stockage d'images des chaînes : mêmes vérifications et même
// recadrage carré que la photo de chaîne (ADR-018), dans le dossier ad-logos/.
export class ChannelImageAdLogoStore implements AdLogoStore {
  constructor(private readonly images: ChannelImageStore) {}

  save(path: string): Promise<string> {
    return this.images.save(path, 'adLogo');
  }

  remove(url: string): Promise<void> {
    return this.images.remove(url);
  }
}
