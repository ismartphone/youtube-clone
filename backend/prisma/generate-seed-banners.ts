// Génère les bannières des chaînes de démonstration (prisma/seed-banners.ts) dans le stockage local.
// Lancement, après « npm run seed:videos » : npm run seed:banners   (--force pour tout régénérer)
//
// Pour chaque chaîne, FFmpeg prend une image de la vidéo source, la met à la largeur de la bannière
// puis la recadre au centre. Une bannière déjà présente est gardée. Nécessite FFmpeg (FFMPEG_PATH).
import { execFile } from 'node:child_process';
import { mkdir, rename, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { promisify } from 'node:util';
import {
  ffmpegCommand,
  mediaDirectory,
} from '../src/shared/infrastructure/media.config.js';
import { SEED_BANNERS, type SeedBanner } from './seed-banners.js';

const run = promisify(execFile);

// Proportions d'un bandeau (environ 3,6 pour 1), comme sur la page de chaîne de l'application.
const BANNER_WIDTH = 2048;
const BANNER_HEIGHT = 568;

try {
  process.loadEnvFile();
} catch {
  // Pas de fichier .env : on garde les variables déjà définies.
}

const ffmpeg = ffmpegCommand();
const mediaDir = mediaDirectory();

async function exists(file: string): Promise<boolean> {
  return stat(file).then(
    () => true,
    () => false,
  );
}

function sourceOf(banner: SeedBanner): string {
  return 'url' in banner.source
    ? banner.source.url
    : join(mediaDir, 'videos', banner.source.localFile);
}

async function generate(banner: SeedBanner, target: string): Promise<void> {
  const source = sourceOf(banner);
  if (!('url' in banner.source) && !(await exists(source))) {
    throw new Error(`vidéo source absente (${source}) : lancez d'abord « npm run seed:videos »`);
  }
  // Fichier temporaire à côté de la cible : une bannière à moitié écrite n'est jamais servie.
  const temporary = `${target}.tmp.jpg`;
  await run(ffmpeg, [
    '-hide_banner',
    '-loglevel',
    'error',
    '-ss',
    String(banner.atSeconds),
    '-i',
    source,
    '-frames:v',
    '1',
    '-vf',
    `scale=${BANNER_WIDTH}:-2,crop=${BANNER_WIDTH}:${BANNER_HEIGHT}`,
    '-q:v',
    '3',
    '-y',
    temporary,
  ]);
  if (!(await exists(temporary))) {
    throw new Error("FFmpeg n'a produit aucune image");
  }
  await rename(temporary, target);
}

async function main() {
  const force = process.argv.includes('--force');
  try {
    await run(ffmpeg, ['-version']);
  } catch {
    console.error(
      `« ${ffmpeg} » est introuvable. Installez FFmpeg (par exemple « winget install Gyan.FFmpeg »), ` +
        'ou indiquez son chemin dans FFMPEG_PATH.',
    );
    process.exit(1);
  }

  const directory = join(mediaDir, 'banners');
  await mkdir(directory, { recursive: true });

  let failures = 0;
  for (const [handle, banner] of Object.entries(SEED_BANNERS)) {
    const target = join(directory, `${handle}.jpg`);
    if (!force && (await exists(target))) {
      console.log(`${handle} : déjà présente`);
      continue;
    }
    try {
      await generate(banner, target);
      console.log(`${handle} : générée`);
    } catch (error) {
      failures++;
      console.error(`${handle} : échec, ${error instanceof Error ? error.message : String(error)}`);
    }
  }
  if (failures > 0) {
    process.exit(1);
  }
}

await main();
