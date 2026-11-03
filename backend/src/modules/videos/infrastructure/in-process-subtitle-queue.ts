import { Logger } from '@nestjs/common';
import type { GenerateSubtitlesUseCase } from '../application/generate-subtitles.use-case.js';
import type { SubtitleQueue } from '../domain/subtitles.js';

/**
 * File de transcription dans le processus du serveur : une vidéo à la fois, indépendante de la
 * file HLS. La publication n'attend jamais : un échec ou un abandon est journalisé et la vidéo
 * reste sans sous-titres (ADR-017). La file est perdue si le serveur s'arrête ; la commande
 * npm run subtitles:generate rattrape alors les vidéos sans sous-titres.
 */
export class InProcessSubtitleQueue implements SubtitleQueue {
  private readonly logger = new Logger(InProcessSubtitleQueue.name);
  private tail: Promise<void> = Promise.resolve();

  constructor(private readonly generateSubtitles: GenerateSubtitlesUseCase) {}

  enqueue(videoId: string): void {
    this.tail = this.tail.then(async () => {
      const startedAt = Date.now();
      try {
        const outcome = await this.generateSubtitles.execute(videoId);
        if (outcome === 'generated') {
          this.logger.log(
            `Sous-titres générés pour ${videoId} en ${Math.round((Date.now() - startedAt) / 1000)} s`,
          );
        }
      } catch (error) {
        this.logger.error(
          `Sous-titres non générés pour ${videoId} : ${(error as Error).message}`,
        );
      }
    });
  }
}
