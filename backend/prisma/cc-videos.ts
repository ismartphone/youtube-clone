// Vidéos sous licence Creative Commons avec attribution (CC BY), publiées sur YouTube par leurs
// auteurs et hébergées chez nous (ADR-002). Attribution obligatoire : voir docs/sources-videos.md.
//
// Contrairement aux vidéos Pexels (seed-videos.ts), ces fichiers ne peuvent pas être retéléchargés
// par un script : ils sont fournis à la main dans storage/media/videos (cc-01.mp4 à cc-06.mp4),
// dossier non versionné. Le seed et « npm run seed:cc-videos » lisent cette même liste.
import { execFile } from 'node:child_process';
import { mkdir, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { promisify } from 'node:util';

const run = promisify(execFile);

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

// Initiales : premières lettres des deux premiers mots, ou des deux premières parties d'un nom
// en un seul mot écrit en CamelCase (« Jazon Gaming » : JG, « BeatEmUps » : BE, « Hamxton » : H).
export function ccInitials(name: string): string {
  const words = name.trim().split(/\s+/);
  const parts =
    words.length > 1
      ? words
      : (name.match(/[A-Z][a-z0-9]*|[a-z0-9]+/g) ?? [name]);
  return parts
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}

// Couleur de fond déduite du nom, stable d'un lancement à l'autre : teinte tirée d'un hachage
// du nom, saturation et luminosité fixes pour que les initiales blanches restent lisibles.
export function ccAvatarColor(name: string): string {
  let hash = 0;
  for (const char of name) {
    hash = (hash * 31 + char.codePointAt(0)!) >>> 0;
  }
  return hslToHex(hash % 360, 0.55, 0.42);
}

function hslToHex(hue: number, saturation: number, lightness: number): string {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const x = chroma * (1 - Math.abs(((hue / 60) % 2) - 1));
  const m = lightness - chroma / 2;
  const [r, g, b] =
    hue < 60
      ? [chroma, x, 0]
      : hue < 120
        ? [x, chroma, 0]
        : hue < 180
          ? [0, chroma, x]
          : hue < 240
            ? [0, x, chroma]
            : hue < 300
              ? [x, 0, chroma]
              : [chroma, 0, x];
  return [r, g, b]
    .map((c) =>
      Math.round((c + m) * 255)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('');
}

const AVATAR_SIZE = 256;

// Police des initiales : AVATAR_FONT si elle est définie, sinon une police courante du système.
// FFmpeg ne trouve pas toujours de police par défaut (fontconfig absent sous Windows).
const FONT_CANDIDATES = [
  process.env.AVATAR_FONT,
  'C:/Windows/Fonts/arialbd.ttf',
  '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
  '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
].filter((font): font is string => Boolean(font));

async function exists(file: string): Promise<boolean> {
  return stat(file).then(
    () => true,
    () => false,
  );
}

async function avatarFont(): Promise<string> {
  for (const font of FONT_CANDIDATES) {
    if (await exists(font)) return font;
  }
  throw new Error(
    'Aucune police trouvée pour les avatars : indiquez un fichier .ttf dans AVATAR_FONT.',
  );
}

/**
 * Génère l'avatar PNG de la chaîne (initiales blanches sur fond de couleur) dans
 * <mediaDir>/avatars, avec FFmpeg. Un avatar déjà présent est gardé. Renvoie true s'il a été créé.
 */
export async function ensureCcAvatar(
  creator: CcCreator,
  ffmpeg: string,
  mediaDir: string,
): Promise<boolean> {
  const folder = join(mediaDir, 'avatars');
  const output = join(folder, `${ccHandle(creator)}.png`);
  if (await exists(output)) return false;
  await mkdir(folder, { recursive: true });
  // Dans un filtre FFmpeg, « : » sépare les options : celui d'un chemin Windows est échappé.
  const font = (await avatarFont()).replace(/\\/g, '/').replace(/:/g, '\\:');
  await run(ffmpeg, [
    '-v',
    'error',
    '-f',
    'lavfi',
    '-i',
    `color=c=0x${ccAvatarColor(creator.name)}:s=${AVATAR_SIZE}x${AVATAR_SIZE}:d=1`,
    '-frames:v',
    '1',
    '-vf',
    `drawtext=text='${ccInitials(creator.name)}':fontfile='${font}':fontcolor=white:fontsize=110:x=(w-text_w)/2:y=(h-text_h)/2`,
    '-y',
    output,
  ]);
  return true;
}
