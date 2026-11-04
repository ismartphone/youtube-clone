import type { Ad } from './ad.js';

// Port vu par le module videos (ADR-021) : il sait si le spectateur est Premium et si la vidéo
// est un Short ; le module ads ne fait que tirer la publicité au sort.

export const AD_PICKER = Symbol('AD_PICKER');

export interface AdPicker {
  // Une publicité tirée au sort, ou null s'il n'y en a aucune.
  pick(): Promise<Ad | null>;
}
