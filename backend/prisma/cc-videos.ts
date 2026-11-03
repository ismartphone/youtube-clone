// Vidéos sous licence Creative Commons avec attribution (CC BY), publiées sur YouTube par leurs
// auteurs et hébergées chez nous (ADR-002). Attribution obligatoire : voir docs/sources-videos.md.
//
// Contrairement aux vidéos Pexels (seed-videos.ts), ces fichiers ne peuvent pas être retéléchargés
// par un script : ils sont fournis à la main dans storage/media/videos (cc-01.mp4 à cc-06.mp4),
// dossier non versionné. Le seed et « npm run seed:cc-videos » lisent cette même liste.
import { stat } from 'node:fs/promises';
import { join } from 'node:path';
import { FfmpegAvatarGenerator } from '../src/modules/channels/infrastructure/ffmpeg-avatar-generator.js';

export interface CcCreator {
  name: string;
  link: string;
}

export interface CcVideo {
  file: string;
  title: string;
  creator: CcCreator;
  category: 'Gaming' | 'Documentaire' | 'Musique';
  format: 'LONG' | 'SHORT';
  // Durée mesurée par ffprobe, arrondie à la seconde.
  durationSeconds: number;
}

const BEAT_EM_UPS = {
  name: 'BeatEmUps',
  link: 'https://www.youtube.com/@BeatEmUps',
};
const GAMING_BEAVER = {
  name: 'TheGamingBeaver',
  link: 'https://www.youtube.com/@AGamingBeaver',
};
const JAZON_GAMING = {
  name: 'Jazon Gaming',
  link: 'https://www.youtube.com/@JazonGaming',
};
const GOOFI_DEV = {
  name: 'GoofiDev',
  link: 'https://www.youtube.com/@GoofiDev',
};
const EDWARD_WKL = {
  name: 'Edward wkl',
  link: 'https://www.youtube.com/@edwardwkl',
};
const HAMXTON = {
  name: 'Hamxton',
  link: 'https://www.youtube.com/channel/UC0KiP4me3XkJmuCFxmZio0w',
};

export const CC_VIDEOS: CcVideo[] = [
  {
    file: 'cc-01.mp4',
    title: "I Spent $5000 on Nintendo Switch 2 Games So You Don't Have To",
    creator: BEAT_EM_UPS,
    category: 'Gaming',
    format: 'LONG',
    durationSeconds: 1658,
  },
  {
    file: 'cc-02.mp4',
    title:
      'This Dinosaur Horror Game Is So Good! | Terrible Lizards - FULL GAME',
    creator: GAMING_BEAVER,
    category: 'Gaming',
    format: 'LONG',
    // Raccourcie aux cinq premières minutes (la vidéo d'origine dure 5 h 21) : voir docs/sources-videos.md.
    durationSeconds: 300,
  },
  {
    file: 'cc-03.mp4',
    title: '"11 SPECTATORS!" Barret gaming (ROS TAGALOG)',
    creator: JAZON_GAMING,
    category: 'Gaming',
    format: 'LONG',
    durationSeconds: 900,
  },
  {
    file: 'cc-04.mp4',
    title: 'I Made My Sheep Too Real #gamedev #indiedev',
    creator: GOOFI_DEV,
    category: 'Gaming',
    format: 'SHORT',
    durationSeconds: 38,
  },
  {
    file: 'cc-05.mp4',
    title:
      "Voici l'endroit le plus profond de la planète (et ce qu'on y a trouvé)",
    creator: EDWARD_WKL,
    category: 'Documentaire',
    format: 'LONG',
    durationSeconds: 794,
  },
  {
    file: 'cc-06.mp4',
    title:
      'HAMXTON- Reviens ft @DILO_Dégaine-la-magie & @thereal_mnl (Clip officiel)',
    creator: HAMXTON,
    category: 'Musique',
    format: 'LONG',
    durationSeconds: 233,
  },
];

// Créateurs distincts, dans l'ordre de la liste.
export const CC_CREATORS: CcCreator[] = [
  ...new Set(CC_VIDEOS.map((v) => v.creator)),
];

// URL enregistrée en base : chemin relatif, complété par chaque client.
export function ccVideoUrl(video: CcVideo): string {
  return `/media/videos/${video.file}`;
}

// Handle dérivé du nom, au format des handles (3 à 30 caractères : a-z, 0-9, point, tirets).
export function ccHandle(creator: CcCreator): string {
  return creator.name
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9._-]/g, '')
    .slice(0, 30);
}

// Compte propriétaire de la chaîne, qui ne sert pas à se connecter (mot de passe aléatoire).
export function ccOwnerEmail(creator: CcCreator): string {
  return `${ccHandle(creator)}@creators.example.com`;
}

export function ccAvatarUrl(creator: CcCreator): string {
  return `/media/avatars/${ccHandle(creator)}.png`;
}

/**
 * Génère l'avatar de la chaîne (initiales sur fond de couleur, comme pour toute chaîne créée par
 * POST /channels) sous <mediaDir>/avatars/<handle>.png. Un avatar déjà présent est gardé.
 * Renvoie true s'il a été créé ; lève une erreur si la génération échoue.
 */
export async function ensureCcAvatar(
  creator: CcCreator,
  ffmpeg: string,
  mediaDir: string,
): Promise<boolean> {
  const handle = ccHandle(creator);
  const exists = await stat(join(mediaDir, 'avatars', `${handle}.png`)).then(
    () => true,
    () => false,
  );
  if (exists) return false;
  const url = await new FfmpegAvatarGenerator({
    command: ffmpeg,
    mediaDir,
  }).generate(creator.name, handle);
  if (!url)
    throw new Error(`avatar de ${creator.name} non généré (voir le journal)`);
  return true;
}
