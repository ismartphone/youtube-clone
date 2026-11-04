import type { Ad } from '../../domain/ad.js';

// Forme exacte des réponses : chaque champ exposé est listé ici.

// Publicité à lire (pré-roll, bandeau) : HLS quand il est prêt, MP4 sinon.
export function presentAd(ad: Ad) {
  return {
    id: ad.id,
    title: ad.title,
    videoUrl: ad.videoUrl,
    hlsUrl: ad.hlsUrl,
    thumbnailUrl: ad.thumbnailUrl,
    durationSeconds: ad.durationSeconds,
    destinationUrl: ad.destinationUrl,
    advertiserName: ad.advertiserName,
    description: ad.description,
    logoUrl: ad.logoUrl,
    ctaLabel: ad.ctaLabel,
  };
}

export function presentAdForAdmin(ad: Ad) {
  return { ...presentAd(ad), createdAt: ad.createdAt };
}
