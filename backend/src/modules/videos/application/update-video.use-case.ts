import type { HlsGenerator, HlsQueue } from '../domain/hls.js';
import type { VideoCatalog, VideoPatch } from '../domain/catalog.js';
import type { VideoCounters } from '../domain/counters.js';
import {
  thumbnailPosition,
  thumbnailSize,
  type ThumbnailGenerator,
} from '../domain/thumbnail-generator.js';
import { ADMIN_VIEWER } from '../domain/video.js';
import type { VideoRepository } from '../domain/video.repository.js';
import type { VideoRoomGuard } from '../../realtime/domain/video-room-guard.js';
import { toVideoViews, type VideoView } from './video-view.js';

export class UpdateVideoUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly catalog: VideoCatalog,
    private readonly counters: VideoCounters,
    private readonly thumbnails: ThumbnailGenerator,
    private readonly rooms: VideoRoomGuard,
    private readonly hls: HlsGenerator,
    private readonly hlsQueue: HlsQueue,
  ) {}

  // Renvoie null si la vidéo n'existe pas.
  async execute(id: string, patch: VideoPatch): Promise<VideoView | null> {
    const current = await this.videos.findById(id);
    if (!current) {
      return null;
    }
    const videoUrl = patch.videoUrl ?? current.videoUrl;
    const keptThumbnail =
      patch.thumbnailUrl === undefined
        ? current.thumbnailUrl
        : patch.thumbnailUrl;
    // Nouvelle miniature si l'administrateur l'a effacée, ou si la vidéo a changé alors que
    // l'ancienne miniature avait été extraite de l'ancienne vidéo.
    const regenerate =
      keptThumbnail === null ||
      (videoUrl !== current.videoUrl &&
        this.thumbnails.isGenerated(keptThumbnail));
    const thumbnailUrl = regenerate
      ? await this.thumbnails.generate(
          videoUrl,
          thumbnailPosition(patch.durationSeconds ?? current.durationSeconds),
          thumbnailSize(patch.format ?? current.format),
        )
      : keptThumbnail;

    let video;
    try {
      // Autre fichier : l'ancienne échelle HLS ne correspond plus, une nouvelle sera générée.
      const fileChanged = videoUrl !== current.videoUrl;
      video = await this.catalog.update(id, {
        ...patch,
        thumbnailUrl,
        ...(fileChanged ? { hlsUrl: null } : {}),
      });
    } catch (error) {
      if (regenerate) await this.thumbnails.remove(thumbnailUrl);
      throw error;
    }
    if (!video) {
      // Supprimée entre-temps.
      if (regenerate) await this.thumbnails.remove(thumbnailUrl);
      return null;
    }
    // L'ancienne miniature générée n'est plus référencée.
    if (current.thumbnailUrl !== thumbnailUrl) {
      await this.thumbnails.remove(current.thumbnailUrl);
    }
    if (video.videoUrl !== current.videoUrl) {
      if (current.hlsUrl) await this.hls.remove(current.hlsUrl);
      this.hlsQueue.enqueue(id);
    }
    // Visibilité changée : les spectateurs qui n'y ont plus droit quittent la salle temps réel,
    // sinon ils continueraient de recevoir commentaires et compteurs (ADR-016).
    if (video.visibility !== current.visibility) {
      await this.rooms.revalidate(id);
    }
    const [view] = await toVideoViews([video], ADMIN_VIEWER, this.counters);
    return view;
  }
}
