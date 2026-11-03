import { Logger } from '@nestjs/common';
import type { GenerateHlsUseCase } from '../application/generate-hls.use-case.js';
import type { HlsQueue } from '../domain/hls.js';

/**
 * File de génération HLS dans le processus du serveur : une vidéo à la fois, pour ne pas
 * saturer la machine. La publication n'attend jamais : un échec est journalisé et la vidéo
 * reste lisible en MP4 (ADR-017). La file est perdue si le serveur s'arrête ; la commande
 * npm run hls:generate rattrape alors les vidéos sans HLS.
 */
export class InProcessHlsQueue implements HlsQueue {
  private readonly logger = new Logger(InProcessHlsQueue.name);
  private tail: Promise<void> = Promise.resolve();

  constructor(private readonly generateHls: GenerateHlsUseCase) {}

  enqueue(videoId: string): void {
    this.tail = this.tail.then(async () => {
      const startedAt = Date.now();
      try {
        const outcome = await this.generateHls.execute(videoId);
        if (outcome === 'generated') {
          this.logger.log(
            `HLS généré pour ${videoId} en ${Math.round((Date.now() - startedAt) / 1000)} s`,
          );
        }
      } catch (error) {
        this.logger.warn(
          `HLS non généré pour ${videoId}, lue en MP4 : ${(error as Error).message}`,
        );
      }
    });
  }
}
