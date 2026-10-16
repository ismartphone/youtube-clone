// Génère en une fois la miniature des vidéos qui n'en ont pas de vraie (placeholder placehold.co
// du seed) : une image extraite à 10 % de la durée. Les miniatures saisies à la main ou déjà
// générées ne sont pas touchées ; pour une vidéo précise, utiliser le bouton du back-office.
// Lancement : npm run thumbnails:regenerate
// Une vidéo en échec est signalée et passée.
import { RegenerateThumbnailUseCase } from '../modules/videos/application/regenerate-thumbnail.use-case.js';
import { isPlaceholderThumbnail } from '../modules/videos/domain/thumbnail-generator.js';
import { FfmpegThumbnailGenerator } from '../modules/videos/infrastructure/ffmpeg-thumbnail-generator.js';
import { PrismaVideoCatalog } from '../modules/videos/infrastructure/prisma-video-catalog.js';
import { PrismaVideoRepository } from '../modules/videos/infrastructure/prisma-video.repository.js';
import {
  ffmpegCommand,
  mediaDirectory,
} from '../shared/infrastructure/media.config.js';
import { PrismaService } from '../shared/infrastructure/prisma.service.js';

try {
  process.loadEnvFile();
} catch {
  // Pas de fichier .env : on garde les variables déjà définies.
}

async function main() {
  const prisma = new PrismaService();
  const thumbnails = new FfmpegThumbnailGenerator({
    command: ffmpegCommand(),
    mediaDir: mediaDirectory(),
  });
  try {
    if (!(await thumbnails.isAvailable())) {
      // Le message détaillé (installation, FFMPEG_PATH) vient d'être affiché par le générateur.
      process.exitCode = 1;
      return;
    }
    const regenerate = new RegenerateThumbnailUseCase(
      new PrismaVideoRepository(prisma),
      new PrismaVideoCatalog(prisma),
      thumbnails,
    );
    const all = await prisma.video.findMany({
      select: { id: true, title: true, thumbnailUrl: true },
      orderBy: { publishedAt: 'asc' },
    });
    const videos = all.filter((video) =>
      isPlaceholderThumbnail(video.thumbnailUrl),
    );
    console.log(
      `${videos.length} vidéo(s) sur ${all.length} sans vraie miniature.\n`,
    );
    let failures = 0;
    for (const [index, video] of videos.entries()) {
      const position = `[${index + 1}/${videos.length}]`;
      try {
        const url = await regenerate.execute(video.id);
        console.log(
          `${position} ${video.title} : ${url ?? 'vidéo supprimée entre-temps'}`,
        );
      } catch (error) {
        failures++;
        console.error(
          `${position} ${video.title} : ${(error as Error).message}`,
        );
      }
    }
    console.log(
      `\nTerminé : ${videos.length - failures} miniature(s) régénérée(s), ${failures} échec(s).`,
    );
    process.exitCode = failures > 0 ? 1 : 0;
  } finally {
    await prisma.$disconnect();
  }
}

await main();
