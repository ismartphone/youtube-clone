import { canSee, type Viewer } from '../domain/video.js';
import type { VideoRepository } from '../domain/video.repository.js';
import type { VideoCounters } from '../domain/counters.js';
import { toVideoViews, type VideoView } from './video-view.js';

export class GetVideoUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly counters: VideoCounters,
  ) {}

  // Renvoie null si la vidéo n'existe pas, ou si elle est privée et que le spectateur n'en est
  // pas le propriétaire : la réponse est la même, pour ne pas révéler son existence (ADR-016).
  async execute(id: string, viewer: Viewer): Promise<VideoView | null> {
    const video = await this.videos.findById(id);
    if (!video || !canSee(video, viewer)) {
      return null;
    }
    const [view] = await toVideoViews([video], viewer, this.counters);
    return view;
  }
}
