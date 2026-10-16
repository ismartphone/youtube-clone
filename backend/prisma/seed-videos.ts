// Vidéos de démonstration hébergées chez nous (ADR-002), servies par le backend sous /media.
// Source : Pexels (licence Pexels, usage gratuit sans attribution).
// Les fichiers ne sont pas versionnés (dossier storage/ ignoré par Git) : après un clone,
// « npm run seed:videos » les télécharge, les vérifie et les réencode (voir fetch-seed-videos.ts).
// Le seed et ce script lisent cette même liste.

export interface SeedVideoFile {
  pexelsId: number;
  orientation: 'landscape' | 'portrait';
  // Durée gardée, coupée au début de la vidéo d'origine (celle annoncée par le titre).
  clipSeconds: number;
}

export const LOCAL_SEED_VIDEOS = {
  // La ville vue d'un train.
  city: { pexelsId: 4832768, orientation: 'landscape', clipSeconds: 30 },
  // Autoroute au coucher du soleil.
  travel: { pexelsId: 11027546, orientation: 'landscape', clipSeconds: 5 },
  // Piétons qui traversent une rue (Short).
  street: { pexelsId: 27697943, orientation: 'portrait', clipSeconds: 10 },
  // Herbes au coucher du soleil (Short).
  breathe: { pexelsId: 35591195, orientation: 'portrait', clipSeconds: 15 },
  // Herbes dans le vent : la vidéo d'origine dure 9,3 s, elle est gardée entière.
  outdoors: { pexelsId: 3987649, orientation: 'landscape', clipSeconds: 9.3 },
} as const satisfies Record<string, SeedVideoFile>;

export function localVideoFileName(video: SeedVideoFile): string {
  return `pexels-${video.pexelsId}.mp4`;
}

// URL enregistrée en base : chemin relatif, complété par chaque client.
export function localVideoUrl(video: SeedVideoFile): string {
  return `/media/videos/${localVideoFileName(video)}`;
}
