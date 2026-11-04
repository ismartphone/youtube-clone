// Génère les sous-titres (ADR-017) des vidéos hébergées chez nous qui n'en ont pas encore :
// vidéos du seed et Creative Commons, ou vidéos dont la transcription en tâche de fond a échoué,
// a été abandonnée ou interrompue par un arrêt du serveur. Les vidéos externes n'en ont jamais.
// Une vidéo sans piste audio, ou sans parole, reste sans sous-titres et sera signalée à nouveau.
// Les vidéos de SUBTITLES_DISABLED_FOR (transcription inexploitable) sont ignorées.
// Lancement : npm run subtitles:generate   (une vidéo à la fois ; au plus 5 minutes par vidéo)
import { GenerateSubtitlesUseCase } from '../modules/videos/application/generate-subtitles.use-case.js';
import { FfmpegSubtitleGenerator } from '../modules/videos/infrastructure/ffmpeg-subtitle-generator.js';
import { PrismaVideoCatalog } from '../modules/videos/infrastructure/prisma-video-catalog.js';
import { PrismaVideoRepository } from '../modules/videos/infrastructure/prisma-video.repository.js';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
  whisperModelPath,
} from '../shared/infrastructure/media.config.js';
import { SUBTITLES_DISABLED_FOR } from '../modules/videos/domain/subtitles.js';
import { PrismaService } from '../shared/infrastructure/prisma.service.js';

try {
  process.loadEnvFile();
} catch {
  // Pas de fichier .env : on garde les variables déjà définies.
}

async function main() {
  const prisma = new PrismaService();
  try {
    const generator = new FfmpegSubtitleGenerator({
      ffmpeg: ffmpegCommand(),
      ffprobe: ffprobeCommand(),
      mediaDir: mediaDirectory(),
      model: whisperModelPath(),
    });
    // Filtre whisper ou modèle absent : l'erreur est déjà journalisée, inutile d'essayer.
    if (!(await generator.isAvailable())) {
      process.exitCode = 1;
      return;
    }
    const generateSubtitles = new GenerateSubtitlesUseCase(
      new PrismaVideoRepository(prisma),
      new PrismaVideoCatalog(prisma),
      generator,
    );
    const videos = await prisma.video.findMany({
      where: {
        subtitleUrl: null,
        videoUrl: {
          startsWith: '/media/videos/',
          notIn: [...SUBTITLES_DISABLED_FOR],
        },
      },
      select: { id: true, title: true },
      orderBy: { publishedAt: 'asc' },
    });
    console.log(`${videos.length} vidéo(s) sans sous-titres.`);
    let failed = 0;
    for (const video of videos) {
      const startedAt = Date.now();
      try {
        await generateSubtitles.execute(video.id);
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
