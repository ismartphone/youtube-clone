import type { VideoCatalog } from '../domain/catalog.js';
import {
  transcriptionTimeoutMs,
  type SubtitleGenerator,
} from '../domain/subtitles.js';
import type { VideoRepository } from '../domain/video.repository.js';

export type SubtitleOutcome = 'generated' | 'skipped' | 'missing';

/**
 * Transcrit une vidéo hébergée chez nous et enregistre ses sous-titres (ADR-017). Une vidéo
 * externe n'en a pas. Si la vidéo a changé de fichier pendant la transcription, ou a été
 * supprimée, les sous-titres produits ne servent plus et ils sont effacés.
 */
export class GenerateSubtitlesUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly catalog: VideoCatalog,
    private readonly subtitles: SubtitleGenerator,
  ) {}

  // Lève une erreur si la transcription échoue ou dépasse son temps : la vidéo reste sans sous-titres.
  async execute(videoId: string): Promise<SubtitleOutcome> {
    const video = await this.videos.findById(videoId);
    if (!video) {
      return 'missing';
    }
    if (!this.subtitles.isLocal(video.videoUrl)) {
      return 'skipped';
    }
    const subtitleUrl = await this.subtitles.generate(
      video.videoUrl,
      transcriptionTimeoutMs(video.durationSeconds),
    );
    const current = await this.videos.findById(videoId);
    if (!current || current.videoUrl !== video.videoUrl) {
      await this.subtitles.remove(subtitleUrl);
      return 'missing';
    }
    await this.catalog.update(videoId, { subtitleUrl });
    // Les anciens sous-titres éventuels ne sont plus référencés.
    if (current.subtitleUrl && current.subtitleUrl !== subtitleUrl) {
      await this.subtitles.remove(current.subtitleUrl);
    }
    return 'generated';
  }
}
