import type { VideoCatalog } from '../domain/catalog.js';
import type { HlsGenerator } from '../domain/hls.js';
import type { VideoRepository } from '../domain/video.repository.js';

export type HlsOutcome = 'generated' | 'skipped' | 'missing';

/**
 * Génère l'échelle HLS d'une vidéo hébergée chez nous et l'enregistre (ADR-017). Une vidéo
 * externe n'en a pas. Si la vidéo a changé de fichier pendant l'encodage, ou a été supprimée,
 * le HLS produit ne sert plus et il est effacé.
 */
export class GenerateHlsUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly catalog: VideoCatalog,
    private readonly hls: HlsGenerator,
  ) {}

  // Lève une erreur si l'encodage échoue : la vidéo reste lisible en MP4.
  async execute(videoId: string): Promise<HlsOutcome> {
    const video = await this.videos.findById(videoId);
    if (!video) {
      return 'missing';
    }
    if (!this.hls.isLocal(video.videoUrl)) {
      return 'skipped';
    }
    const hlsUrl = await this.hls.generate(video.videoUrl);
    const current = await this.videos.findById(videoId);
    if (!current || current.videoUrl !== video.videoUrl) {
      await this.hls.remove(hlsUrl);
      return 'missing';
    }
    await this.catalog.update(videoId, { hlsUrl });
    // L'ancienne échelle éventuelle n'est plus référencée.
    if (current.hlsUrl && current.hlsUrl !== hlsUrl) {
      await this.hls.remove(current.hlsUrl);
    }
    return 'generated';
  }
}
