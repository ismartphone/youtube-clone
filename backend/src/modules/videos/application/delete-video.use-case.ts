import type { VideoCatalog } from '../domain/catalog.js';
import type { ThumbnailGenerator } from '../domain/thumbnail-generator.js';
import type { VideoRepository } from '../domain/video.repository.js';

export class DeleteVideoUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly catalog: VideoCatalog,
    private readonly thumbnails: ThumbnailGenerator,
  ) {}

  // Renvoie false si la vidéo n'existe pas. Ses commentaires, « j'aime » et historiques
  // sont supprimés avec elle (cascade du schéma). Le fichier vidéo, lui, n'est pas touché ;
  // la miniature, si elle a été générée par le serveur, est supprimée.
  async execute(id: string): Promise<boolean> {
    const video = await this.videos.findById(id);
    if (!video || !(await this.catalog.delete(id))) {
      return false;
    }
    await this.thumbnails.remove(video.thumbnailUrl);
    return true;
  }
}
