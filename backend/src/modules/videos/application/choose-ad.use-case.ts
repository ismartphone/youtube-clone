import type { Ad } from '../../ads/domain/ad.js';
import type { AdPicker } from '../../ads/domain/ad-picker.js';
import type { Viewer, VideoFormat } from '../domain/video.js';

// Publicité à montrer (ADR-021), décidée ici et jamais par l'application : aucune pour un compte
// Premium, aucune avant un Short. La lecture hors ligne ne passe pas par le serveur, donc jamais
// de publicité avant une vidéo téléchargée.
export class ChooseAdUseCase {
  constructor(private readonly ads: AdPicker) {}

  // Avant la lecture d'une vidéo.
  beforeVideo(viewer: Viewer, format: VideoFormat): Promise<Ad | null> {
    if (viewer.isPremium || format === 'SHORT') {
      return Promise.resolve(null);
    }
    return this.ads.pick();
  }

  // Bandeau sponsorisé en haut du fil.
  sponsored(viewer: Viewer): Promise<Ad | null> {
    return viewer.isPremium ? Promise.resolve(null) : this.ads.pick();
  }
}
