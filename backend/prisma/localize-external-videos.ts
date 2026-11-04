// Rapatrie dans le stockage local toute vidéo du catalogue dont le fichier est une URL externe :
// l'application doit fonctionner sans Internet, avec le seul serveur local.
// Lancement : npm run videos:localize   (Internet nécessaire pour le rapatriement lui-même)
//
// Pour chaque vidéo externe, même procédé que seed:videos (fetch-seed-videos.ts) : téléchargement,
// vérification ffprobe et décodage complet, réencodage 720p, vérification finale. Une vidéo du seed
// reprend son fichier local (seed-videos.ts) ; une autre est rangée sous /media/videos/ext-<id>.mp4.
// Puis l'URL passe en chemin local, la durée est corrigée si besoin, et la miniature (si elle était
// externe), l'échelle HLS et les sous-titres sont régénérés.
// Une vidéo dont la source est injoignable ou refuse le téléchargement est retirée du catalogue,
// plutôt que de rester muette. Un échec de traitement chez nous (vérification, encodage) la laisse
// en place, signalée : ce n'est pas une raison de supprimer.
import 'dotenv/config';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from '../src/generated/prisma/client.js';
import { GenerateHlsUseCase } from '../src/modules/videos/application/generate-hls.use-case.js';
import { GenerateSubtitlesUseCase } from '../src/modules/videos/application/generate-subtitles.use-case.js';
import {
  thumbnailPosition,
  thumbnailSize,
} from '../src/modules/videos/domain/thumbnail-generator.js';
import { FfmpegHlsGenerator } from '../src/modules/videos/infrastructure/ffmpeg-hls-generator.js';
import { FfmpegSubtitleGenerator } from '../src/modules/videos/infrastructure/ffmpeg-subtitle-generator.js';
import { FfmpegThumbnailGenerator } from '../src/modules/videos/infrastructure/ffmpeg-thumbnail-generator.js';
import { PrismaVideoCatalog } from '../src/modules/videos/infrastructure/prisma-video-catalog.js';
import { PrismaVideoRepository } from '../src/modules/videos/infrastructure/prisma-video.repository.js';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
  whisperModelPath,
} from '../src/shared/infrastructure/media.config.js';
import { PrismaService } from '../src/shared/infrastructure/prisma.service.js';
import {
  checkTools,
  fetchVideoFile,
  SourceUnavailableError,
} from './fetch-seed-videos.js';
import {
  LOCAL_SEED_VIDEOS,
  localVideoFileName,
  localVideoUrl,
  sourceUrl,
  type SeedVideoFile,
} from './seed-videos.js';

const isLocal = (url: string) => url.startsWith('/media/');

// Vidéo du seed dont l'adresse (ou une ancienne adresse) correspond, sinon procédé générique.
function specFor(video: {
  id: string;
  videoUrl: string;
  format: 'SHORT' | 'LONG';
  durationSeconds: number;
}): SeedVideoFile {
  const seed = (Object.values(LOCAL_SEED_VIDEOS) as SeedVideoFile[]).find(
    (entry) =>
      sourceUrl(entry) === video.videoUrl ||
      entry.aliases?.includes(video.videoUrl),
  );
  return (
    seed ?? {
      url: video.videoUrl,
      fileName: `ext-${video.id}.mp4`,
      orientation: video.format === 'SHORT' ? 'portrait' : 'landscape',
      clipSeconds: video.durationSeconds,
      // Un Short tiré d'une source horizontale est recadré au centre.
      cropToOrientation: video.format === 'SHORT',
    }
  );
}

async function main() {
  if (!(await checkTools())) {
    process.exitCode = 1;
    return;
  }
  const mediaDir = mediaDirectory();
  const prisma = new PrismaClient({
    adapter: new PrismaPg({ connectionString: process.env['DATABASE_URL'] }),
  });
  const service = new PrismaService();
  const videos = new PrismaVideoRepository(service);
  const catalog = new PrismaVideoCatalog(service);
  const thumbnails = new FfmpegThumbnailGenerator({
    command: ffmpegCommand(),
    mediaDir,
  });
  const generateHls = new GenerateHlsUseCase(
    videos,
    catalog,
    new FfmpegHlsGenerator({
      ffmpeg: ffmpegCommand(),
      ffprobe: ffprobeCommand(),
      mediaDir,
    }),
  );
  const generateSubtitles = new GenerateSubtitlesUseCase(
    videos,
    catalog,
    new FfmpegSubtitleGenerator({
      ffmpeg: ffmpegCommand(),
      ffprobe: ffprobeCommand(),
      mediaDir,
      model: whisperModelPath(),
    }),
  );

  const folder = join(mediaDir, 'videos');
  await mkdir(folder, { recursive: true });
  const work = await mkdtemp(join(tmpdir(), 'localize-videos-'));
  const external = await prisma.video.findMany({
    where: {
      OR: [
        { NOT: { videoUrl: { startsWith: '/media/' } } },
        { NOT: { thumbnailUrl: { startsWith: '/media/' } } },
      ],
    },
    select: {
      id: true,
      title: true,
      videoUrl: true,
      thumbnailUrl: true,
      format: true,
      durationSeconds: true,
    },
  });
  console.log(`${external.length} vidéo(s) à rapatrier.`);
  let localized = 0;
  const removed: string[] = [];
  const failed: string[] = [];
  try {
    for (const video of external) {
      const label = `« ${video.title} »`;
      try {
        let videoUrl = video.videoUrl;
        let durationSeconds = video.durationSeconds;
        if (!isLocal(videoUrl)) {
          const spec = specFor(video);
          const target = join(folder, localVideoFileName(spec));
          // Fichier déjà rapatrié (par seed:videos, ou une vidéo du seed partagée) : on le garde.
          const duration = existsSync(target)
            ? video.durationSeconds
            : await fetchVideoFile(spec, target, work);
          videoUrl = localVideoUrl(spec);
          durationSeconds = Math.max(1, Math.round(duration));
        }
        const thumbnailUrl = isLocal(video.thumbnailUrl)
          ? video.thumbnailUrl
          : await thumbnails.generate(
              videoUrl,
              thumbnailPosition(durationSeconds),
              thumbnailSize(video.format),
            );
        await prisma.video.update({
          where: { id: video.id },
          data: {
            videoUrl,
            durationSeconds,
            thumbnailUrl,
            hlsUrl: null,
            subtitleUrl: null,
          },
        });
        const hls = await generateHls.execute(video.id);
        const subtitles = await generateSubtitles
          .execute(video.id)
          .then((outcome) => outcome)
          .catch((error: Error) => `aucun (${error.message})`);
        console.log(
          `  ✓ ${label} → ${videoUrl} (${durationSeconds} s), HLS : ${hls}, sous-titres : ${subtitles}`,
        );
        localized++;
      } catch (error) {
        if (!(error instanceof SourceUnavailableError)) {
          failed.push(`${label} (${(error as Error).message})`);
          console.error(
            `  ! ${label} gardée, à reprendre : ${(error as Error).message}`,
          );
          continue;
        }
        // Source irrécupérable : la vidéo est retirée plutôt que de rester muette sans Internet.
        await prisma.video.delete({ where: { id: video.id } });
        if (isLocal(video.thumbnailUrl))
          await thumbnails.remove(video.thumbnailUrl);
        removed.push(`${label} (${(error as Error).message})`);
        console.error(
          `  ✗ ${label} retirée du catalogue : ${(error as Error).message}`,
        );
      }
    }
  } finally {
    await rm(work, { recursive: true, force: true });
    await prisma.$disconnect();
    await service.$disconnect();
  }
  console.log(
    `\nTerminé : ${localized} vidéo(s) rapatriée(s), ${removed.length} retirée(s), ${failed.length} à reprendre.`,
  );
  if (removed.length + failed.length > 0) process.exitCode = 1;
}

await main();
