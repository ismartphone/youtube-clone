// Génère l'avatar des chaînes qui n'en ont pas (initiales sur fond de couleur), par exemple
// Carnet de Route et Studio Libre, créées par le seed avant la génération automatique.
// Les chaînes créées par POST /channels reçoivent déjà le leur. Une chaîne en échec est signalée.
// Lancement : npm run avatars:generate
import { GenerateMissingAvatarsUseCase } from '../modules/channels/application/create-channel.use-case.js';
import { FfmpegAvatarGenerator } from '../modules/channels/infrastructure/ffmpeg-avatar-generator.js';
import { PrismaChannelRepository } from '../modules/channels/infrastructure/prisma-channel.repository.js';
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
  try {
    const useCase = new GenerateMissingAvatarsUseCase(
      new PrismaChannelRepository(prisma),
      new FfmpegAvatarGenerator({
        command: ffmpegCommand(),
        mediaDir: mediaDirectory(),
      }),
    );
    const { generated, failed } = await useCase.execute();
    console.log(`${generated} avatar(s) généré(s).`);
    if (failed.length > 0) {
      console.warn(`En échec : ${failed.join(', ')}`);
      process.exitCode = 1;
    }
  } finally {
    await prisma.$disconnect();
  }
}

void main();
