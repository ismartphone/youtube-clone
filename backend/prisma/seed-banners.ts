// Bannières des chaînes de démonstration, hébergées chez nous comme les vidéos (ADR-002).
// Chacune est une image extraite d'une vidéo de la chaîne, recadrée en bandeau.
// Les fichiers ne sont pas versionnés (dossier storage/ ignoré par Git) : après
// « npm run seed:videos », « npm run seed:banners » les génère (voir generate-seed-banners.ts).
// Le seed et ce script lisent cette même liste.
import { LOCAL_SEED_VIDEOS, localVideoFileName } from './seed-videos.js';

export interface SeedBanner {
  // Vidéo source : URL distante, ou fichier du dossier media/videos.
  source: { url: string } | { localFile: string };
  // Instant de la vidéo où l'image est prise.
  atSeconds: number;
}

// Clés : handle de la chaîne, qui donne aussi le nom du fichier.
export const SEED_BANNERS = {
  // Les dunes de Sintel, au milieu de la bande-annonce.
  studiolibre: {
    source: { url: 'https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4' },
    atSeconds: 38,
  },
  // La ville vue d'un train.
  carnetderoute: {
    source: { localFile: localVideoFileName(LOCAL_SEED_VIDEOS.city) },
    atSeconds: 12,
  },
} as const satisfies Record<string, SeedBanner>;

// URL enregistrée en base : chemin relatif, complété par chaque client.
export function seedBannerUrl(handle: keyof typeof SEED_BANNERS): string {
  return `/media/banners/${handle}.jpg`;
}
