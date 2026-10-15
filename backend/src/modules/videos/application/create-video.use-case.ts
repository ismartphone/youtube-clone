import type { VideoCatalog, VideoInput } from '../domain/catalog.js';
import { ADMIN_VIEWER } from '../domain/video.js';
import type { VideoCounters } from '../domain/counters.js';
import { toVideoViews, type VideoView } from './video-view.js';

// Publication d'une vidéo depuis le back-office, à partir d'URL déjà hébergées.
export class CreateVideoUseCase {
  constructor(
    private readonly catalog: VideoCatalog,
    private readonly counters: VideoCounters,
  ) {}

  async execute(input: VideoInput): Promise<VideoView> {
    const video = await this.catalog.create(input);
    const [view] = await toVideoViews([video], ADMIN_VIEWER, this.counters);
    return view;
  }
}
