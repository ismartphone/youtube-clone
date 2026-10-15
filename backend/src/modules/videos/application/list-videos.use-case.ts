import type { Viewer } from '../domain/video.js';
import type {
  Page,
  Pagination,
  VideoFilter,
  VideoRepository,
} from '../domain/video.repository.js';
import type { ViewCounter } from '../domain/view-counter.js';
import { toVideoViews, type VideoView } from './video-view.js';

export class ListVideosUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly viewCounter: ViewCounter,
  ) {}

  async execute(
    filter: VideoFilter,
    pagination: Pagination,
    viewer: Viewer,
  ): Promise<Page<VideoView>> {
    const page = await this.videos.findPage(filter, pagination);
    return {
      ...page,
      items: await toVideoViews(page.items, viewer, this.viewCounter),
    };
  }
}
