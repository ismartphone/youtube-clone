import type { VideoCatalog, VideoPatch } from '../domain/catalog.js';
import { ADMIN_VIEWER } from '../domain/video.js';
import type { VideoCounters } from '../domain/counters.js';
import { toVideoViews, type VideoView } from './video-view.js';

export class UpdateVideoUseCase {
  constructor(
    private readonly catalog: VideoCatalog,
    private readonly counters: VideoCounters,
  ) {}

  // Renvoie null si la vidéo n'existe pas.
  async execute(id: string, patch: VideoPatch): Promise<VideoView | null> {
    const video = await this.catalog.update(id, patch);
    if (!video) {
      return null;
    }
    const [view] = await toVideoViews([video], ADMIN_VIEWER, this.counters);
    return view;
  }
}
