// Enregistre une publicité déposée à la main dans storage/media/ads/ (ADR-021), sur le modèle de
// seed:cc-videos : le fichier est vérifié par ffprobe, rangé sous un nom généré, sa miniature
// extraite et son échelle HLS générée. Les champs de la carte du bandeau sponsorisé sont saisis
// (titre, site, annonceur, description) : le titre n'est jamais déduit du nom du fichier. Ils se
// modifient ensuite dans la page Publicités, qui ajoute aussi le logo et le libellé du bouton.
// L'original n'est retiré qu'une fois sa copie enregistrée : un fichier refusé reste en place.
// Lancement : npm run ads:import -- "<fichier dans storage/media/ads>" "<titre>" "<URL du site>" "<annonceur>" "<description>" "<URL du site>"
// Sans argument : liste les fichiers du dossier pas encore enregistrés.
import { copyFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { ManageAdsUseCase } from '../modules/ads/application/ads.use-cases.js';
import { AD_TITLE_MAX_LENGTH } from '../modules/ads/domain/ad.js';
import {
  ADS_FOLDER,
  ADS_URL_PREFIX,
  LocalAdFileStore,
} from '../modules/ads/infrastructure/local-ad-file-store.js';
import { PrismaAdRepository } from '../modules/ads/infrastructure/prisma-ad.repository.js';
import { ChannelImageAdLogoStore } from '../modules/ads/infrastructure/channel-image-ad-logo-store.js';
import { LocalChannelImageStore } from '../modules/channels/infrastructure/local-channel-image-store.js';
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
    new ChannelImageAdLogoStore(
      new LocalChannelImageStore({
        ffmpeg: ffmpegCommand(),
        ffprobe: ffprobeCommand(),
        mediaDir,
      }),
    ),
  );
  try {
    const folder = join(mediaDir, ADS_FOLDER);
    const [fileArg, titleArg, urlArg, advertiserArg, descriptionArg] =
      process.argv.slice(2);
    if (!fileArg) {
      const known = new Set(await repository.findVideoUrls());
      const files = (await readdir(folder, { withFileTypes: true }))
        .filter((entry) => entry.isFile())
        .map((entry) => entry.name)
        .filter((name) => !known.has(ADS_URL_PREFIX + name));
      if (files.length === 0) {
        console.log('Aucune nouvelle publicité dans storage/media/ads/.');
      } else {
        console.log('À enregistrer, un fichier à la fois :');
        for (const name of files) {
          console.log(
            `  npm run ads:import -- "${name}" "<titre>" "<URL du site>" "<annonceur>" "<description>"`,
          );
        }
      }
      return;
    }
    const name = basename(fileArg);
    const title = titleArg?.trim() ?? '';
    if (title.length === 0 || title.length > AD_TITLE_MAX_LENGTH) {
      console.error(
        `Donnez un titre de 1 à ${AD_TITLE_MAX_LENGTH} caractères : npm run ads:import -- "${name}" "<titre>" "<URL du site>" "<annonceur>" "<description>"`,
      );
      process.exitCode = 1;
      return;
    }
    const destinationUrl = urlArg?.trim() ?? '';
    if (!/^https?:\/\/\S+$/.test(destinationUrl)) {
      console.error(
        `Donnez l'adresse complète du site de l'annonceur (https://...) : npm run ads:import -- "${name}" "${title}" "<URL du site>"`,
      );
      process.exitCode = 1;
      return;
    }
    const advertiserName = advertiserArg?.trim() ?? '';
    const description = descriptionArg?.trim() ?? '';
    if (!advertiserName || !description) {
      console.error(
        `Donnez aussi le nom de l'annonceur et une description courte : npm run ads:import -- "${name}" "${title}" "${destinationUrl}" "<annonceur>" "<description>"`,
      );
      process.exitCode = 1;
      return;
    }
    // Une copie est importée : l'original reste intact si elle est refusée.
    const copy = join(tmpdir(), `ad-${randomUUID()}`);
    await copyFile(join(folder, name), copy);
    try {
      const ad = await ads.import(
        copy,
        { title, destinationUrl, advertiserName, description },
        true,
      );
      await rm(join(folder, name), { force: true });
      console.log(
        `  ✓ ${name} → « ${ad.title} », ${ad.videoUrl} (${ad.durationSeconds} s)`,
      );
    } catch (error) {
      await rm(copy, { force: true });
      console.error(`  ! ${name} refusé : ${(error as Error).message}`);
      process.exitCode = 1;
    }
  } finally {
    await prisma.$disconnect();
  }
}

void main();
