// Génère l'échelle HLS (ADR-017) des vidéos hébergées chez nous qui n'en ont pas encore :
// vidéos du seed et Creative Commons, ou vidéos dont la génération en tâche de fond a échoué
// ou a été interrompue par un arrêt du serveur. Les vidéos externes n'en ont jamais.
// Lancement : npm run hls:generate   (une vidéo à la fois ; compter quelques minutes)
import { GenerateHlsUseCase } from '../modules/videos/application/generate-hls.use-case.js';
import { FfmpegHlsGenerator } from '../modules/videos/infrastructure/ffmpeg-hls-generator.js';
import { PrismaVideoCatalog } from '../modules/videos/infrastructure/prisma-video-catalog.js';
import { PrismaVideoRepository } from '../modules/videos/infrastructure/prisma-video.repository.js';
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
  const prisma = new PrismaService();
  try {
    const generateHls = new GenerateHlsUseCase(
      new PrismaVideoRepository(prisma),
      new PrismaVideoCatalog(prisma),
      new FfmpegHlsGenerator({
        ffmpeg: ffmpegCommand(),
        ffprobe: ffprobeCommand(),
        mediaDir: mediaDirectory(),
      }),
    );
    const videos = await prisma.video.findMany({
      where: { hlsUrl: null, videoUrl: { startsWith: '/media/videos/' } },
      select: { id: true, title: true },
      orderBy: { publishedAt: 'asc' },
    });
    console.log(`${videos.length} vidéo(s) sans HLS.`);
    let failed = 0;
    for (const video of videos) {
      const startedAt = Date.now();
      try {
        await generateHls.execute(video.id);
        console.log(
          `  ✓ ${video.title} (${Math.round((Date.now() - startedAt) / 1000)} s)`,
        );
      } catch (error) {
        failed++;
        console.warn(`  ✗ ${video.title} : ${(error as Error).message}`);
      }
    }
    if (failed > 0) {
      process.exitCode = 1;
    }
  } finally {
    await prisma.$disconnect();
  }
}

void main();
