import type { VideoCatalog } from '../domain/catalog.js';
import {
  thumbnailPosition,
  thumbnailSize,
  type ThumbnailGenerator,
} from '../domain/thumbnail-generator.js';
import type { VideoRepository } from '../domain/video.repository.js';

// Remplace la miniature d'une vidéo par une image extraite à 10 % de sa durée.
// Utilisé par le bouton du back-office et par la commande thumbnails:regenerate.
export class RegenerateThumbnailUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly catalog: VideoCatalog,
    private readonly thumbnails: ThumbnailGenerator,
  ) {}

  // Renvoie la nouvelle URL, ou null si la vidéo n'existe pas.
  async execute(id: string): Promise<string | null> {
    const video = await this.videos.findById(id);
    if (!video) {
      return null;
    }
    const thumbnailUrl = await this.thumbnails.generate(
      video.videoUrl,
      thumbnailPosition(video.durationSeconds),
      thumbnailSize(video.format),
    );
    const updated = await this.catalog.update(id, { thumbnailUrl });
    if (!updated) {
      await this.thumbnails.remove(thumbnailUrl);
      return null;
    }
    // L'ancienne miniature générée n'est plus référencée ; une URL externe, elle, n'est pas touchée.
    await this.thumbnails.remove(video.thumbnailUrl);
    return thumbnailUrl;
  }
}
