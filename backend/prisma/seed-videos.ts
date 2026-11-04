// Vidéos de démonstration hébergées chez nous (ADR-002), servies par le backend sous /media.
// Sources : Pexels (licence Pexels, usage gratuit sans attribution), et fichiers de test libres
// (Blender Foundation, Creative Commons Attribution ; test-videos.co.uk, images de projets libres).
// Les fichiers ne sont pas versionnés (dossier storage/ ignoré par Git) : après un clone,
// « npm run seed:videos » les télécharge, les vérifie et les réencode (voir fetch-seed-videos.ts).
// Le seed et ce script lisent cette même liste : aucune vidéo du seed ne dépend d'Internet.

export interface SeedVideoFile {
  // Source : identifiant Pexels, ou adresse directe d'un fichier MP4 (url, avec fileName).
  pexelsId?: number;
  url?: string;
  // Nom du fichier local d'une source qui n'est pas Pexels.
  fileName?: string;
  // Anciennes adresses de la même vidéo, déjà enregistrées en base (videos:localize).
  aliases?: readonly string[];
  orientation: 'landscape' | 'portrait';
  // Durée gardée, coupée au début de la vidéo d'origine (celle annoncée par le titre).
  clipSeconds: number;
  // Source dans l'autre orientation, recadrée au centre : un Short tiré d'une vidéo paysage.
  cropToOrientation?: boolean;
}

const TEST_VIDEOS = 'https://test-videos.co.uk/vids';

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
  // Bande-annonce de Sintel (Blender), source 1080p plutôt que l'ancienne 480p.
  sintelTrailer: {
    url: 'https://download.blender.org/durian/trailer/sintel_trailer-1080p.mp4',
    fileName: 'sintel-trailer.mp4',
    aliases: [
      'https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4',
    ],
    orientation: 'landscape',
    clipSeconds: 52,
  },
  bigBuckBunny: {
    url: `${TEST_VIDEOS}/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4`,
    fileName: 'big-buck-bunny.mp4',
    orientation: 'landscape',
    clipSeconds: 10,
  },
  sintelHd: {
    url: `${TEST_VIDEOS}/sintel/mp4/h264/1080/Sintel_1080_10s_1MB.mp4`,
    fileName: 'sintel-hd.mp4',
    orientation: 'landscape',
    clipSeconds: 10,
  },
  // Short : la source est horizontale, recadrée au centre en vertical.
  jellyfishShort: {
    url: `${TEST_VIDEOS}/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4`,
    fileName: 'jellyfish-short.mp4',
    orientation: 'portrait',
    clipSeconds: 10,
    cropToOrientation: true,
  },
  jellyfish: {
    url: `${TEST_VIDEOS}/jellyfish/mp4/h264/720/Jellyfish_720_10s_5MB.mp4`,
    fileName: 'jellyfish.mp4',
    orientation: 'landscape',
    clipSeconds: 10,
  },
} as const satisfies Record<string, SeedVideoFile>;

export function localVideoFileName(video: SeedVideoFile): string {
  return video.fileName ?? `pexels-${video.pexelsId}.mp4`;
}

// URL enregistrée en base : chemin relatif, complété par chaque client.
export function localVideoUrl(video: SeedVideoFile): string {
  return `/media/videos/${localVideoFileName(video)}`;
}

// Adresse de téléchargement de la vidéo d'origine.
export function sourceUrl(video: SeedVideoFile): string {
  return (
    video.url ?? `https://www.pexels.com/download/video/${video.pexelsId}/`
  );
}
