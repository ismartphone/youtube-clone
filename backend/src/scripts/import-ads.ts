// Enregistre les publicités déposées à la main dans storage/media/ads/ (ADR-021), sur le modèle de
// seed:cc-videos : chaque fichier est vérifié par ffprobe, rangé sous un nom généré, sa miniature
// extraite et son échelle HLS générée. Le titre est le nom du fichier, sans extension.
// Un fichier déjà enregistré n'est pas repris : l'original n'est retiré qu'une fois sa copie
// enregistrée, et un fichier refusé reste en place, signalé.
// Lancement : npm run ads:import
import { copyFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { extname, join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { ManageAdsUseCase } from '../modules/ads/application/ads.use-cases.js';
import { AD_TITLE_MAX_LENGTH } from '../modules/ads/domain/ad.js';
import {
  ADS_FOLDER,
  ADS_URL_PREFIX,
  LocalAdFileStore,
} from '../modules/ads/infrastructure/local-ad-file-store.js';
import { PrismaAdRepository } from '../modules/ads/infrastructure/prisma-ad.repository.js';
import { FfmpegHlsGenerator } from '../modules/videos/infrastructure/ffmpeg-hls-generator.js';
import { FfmpegThumbnailGenerator } from '../modules/videos/infrastructure/ffmpeg-thumbnail-generator.js';
import { FfprobeVideoProber } from '../modules/videos/infrastructure/ffprobe-video-prober.js';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
} from '../shared/infrastructure/media.config.js';
import { PrismaService } from '../shared/infrastructure/prisma.service.js';

try {
  process.loadEnvFile();
} catch {
  // Pas de fichier .env : on garde les variables déjà définies.
}

async function main() {
  const mediaDir = mediaDirectory();
  const prisma = new PrismaService();
  const repository = new PrismaAdRepository(prisma);
  const ads = new ManageAdsUseCase(
    repository,
    new LocalAdFileStore(mediaDir),
    new FfprobeVideoProber(ffprobeCommand()),
    new FfmpegThumbnailGenerator({ command: ffmpegCommand(), mediaDir }),
    new FfmpegHlsGenerator({
      ffmpeg: ffmpegCommand(),
      ffprobe: ffprobeCommand(),
      mediaDir,
    }),
  );
  try {
    const folder = join(mediaDir, ADS_FOLDER);
    const known = new Set(await repository.findVideoUrls());
    const files = (await readdir(folder, { withFileTypes: true }))
      .filter((entry) => entry.isFile())
      .map((entry) => entry.name)
      .filter((name) => !known.has(ADS_URL_PREFIX + name));
    if (files.length === 0) {
      console.log('Aucune nouvelle publicité dans storage/media/ads/.');
      return;
    }
    let failures = 0;
    for (const name of files) {
      const title = name
        .slice(0, name.length - extname(name).length)
        .trim()
        .slice(0, AD_TITLE_MAX_LENGTH);
      // Une copie est importée : l'original reste intact si elle est refusée.
      const copy = join(tmpdir(), `ad-${randomUUID()}`);
      await copyFile(join(folder, name), copy);
      try {
        const ad = await ads.import(copy, title, true);
        await rm(join(folder, name), { force: true });
        console.log(`  ✓ ${name} → ${ad.videoUrl} (${ad.durationSeconds} s)`);
      } catch (error) {
        failures++;
        await rm(copy, { force: true });
        console.error(`  ! ${name} refusé : ${(error as Error).message}`);
      }
    }
    if (failures > 0) process.exitCode = 1;
  } finally {
    await prisma.$disconnect();
  }
}

void main();
