import type { Viewer } from '../domain/video.js';
import type { VideoRepository } from '../domain/video.repository.js';
import type { ViewCounter } from '../domain/view-counter.js';
import { toVideoViews, type VideoView } from './video-view.js';

export class GetVideoUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly viewCounter: ViewCounter,
  ) {}

  async execute(id: string, viewer: Viewer): Promise<VideoView | null> {
    const video = await this.videos.findById(id);
    if (!video) {
      return null;
    }
    const [view] = await toVideoViews([video], viewer, this.viewCounter);
    return view;
  }
}
