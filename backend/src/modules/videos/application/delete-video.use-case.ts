import type { VideoCatalog } from '../domain/catalog.js';
import type { HlsGenerator } from '../domain/hls.js';
import type { ThumbnailGenerator } from '../domain/thumbnail-generator.js';
import type { VideoFileStore } from '../domain/video-upload.js';
import type { VideoRepository } from '../domain/video.repository.js';

export class DeleteVideoUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly catalog: VideoCatalog,
    private readonly thumbnails: ThumbnailGenerator,
    private readonly hls: HlsGenerator,
    private readonly files: VideoFileStore,
  ) {}

  // Renvoie false si la vidéo n'existe pas. Ses commentaires, « j'aime » et historiques
  // sont supprimés avec elle (cascade du schéma), ainsi que les fichiers générés par le serveur :
  // miniature, échelle HLS, et MP4 envoyé depuis l'application (ADR-017). Les MP4 du seed et
  // des vidéos Creative Commons, partagés avec les données de démonstration et impossibles à
  // retélécharger pour les seconds, restent sur le disque.
  async execute(id: string): Promise<boolean> {
    const video = await this.videos.findById(id);
    if (!video || !(await this.catalog.delete(id))) {
      return false;
    }
    await this.thumbnails.remove(video.thumbnailUrl);
    if (video.hlsUrl) await this.hls.remove(video.hlsUrl);
    await this.files.remove(video.videoUrl);
    return true;
  }
}
