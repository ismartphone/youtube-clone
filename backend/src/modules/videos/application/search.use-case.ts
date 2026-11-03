import type { VideoCounters } from '../domain/counters.js';
import type {
  ChannelSearchResult,
  SearchRepository,
} from '../domain/search.repository.js';
import type { Viewer } from '../domain/video.js';
import type { Page, Pagination } from '../domain/video.repository.js';
import { toVideoViews, type VideoView } from './video-view.js';

export interface SearchResults {
  videos: Page<VideoView>;
  channels: Page<ChannelSearchResult>;
}

// Recherche dans les titres de vidéos et les noms de chaînes. Les deux sections sont
// paginées ensemble : la page N contient la page N de chacune.
export class SearchUseCase {
  constructor(
    private readonly search: SearchRepository,
    private readonly counters: VideoCounters,
  ) {}

  async execute(
    term: string,
    pagination: Pagination,
    viewer: Viewer,
  ): Promise<SearchResults> {
    const [videos, channels] = await Promise.all([
      this.search.searchVideos(term, pagination),
      this.search.searchChannels(term, pagination),
    ]);
    return {
      videos: {
        ...videos,
        items: await toVideoViews(videos.items, viewer, this.counters),
      },
      channels,
    };
  }
}
