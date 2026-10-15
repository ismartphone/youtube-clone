import type { Viewer } from '../domain/video.js';
import type { VideoRepository } from '../domain/video.repository.js';
import type { VideoCounters } from '../domain/counters.js';
import { toVideoViews, type VideoView } from './video-view.js';

export class GetVideoUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly counters: VideoCounters,
  ) {}

  async execute(id: string, viewer: Viewer): Promise<VideoView | null> {
    const video = await this.videos.findById(id);
    if (!video) {
      return null;
    }
    const [view] = await toVideoViews([video], viewer, this.counters);
    return view;
  }
}
