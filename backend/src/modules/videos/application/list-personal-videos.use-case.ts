import type { VideoCounters } from '../domain/counters.js';
import type { LikeRepository } from '../domain/like.repository.js';
import type { Viewer } from '../domain/video.js';
import type { Page, Pagination } from '../domain/video.repository.js';
import type { WatchHistoryRepository } from '../domain/watch-history.repository.js';
import { toVideoViews, type VideoView } from './video-view.js';

// Listes personnelles de l'utilisateur connecté, affichées dans l'onglet « Vous » de l'application.

export class ListWatchHistoryUseCase {
  constructor(
    private readonly history: WatchHistoryRepository,
    private readonly counters: VideoCounters,
  ) {}

  async execute(
    userId: string,
    pagination: Pagination,
    viewer: Viewer,
  ): Promise<Page<VideoView>> {
    const page = await this.history.findPage(userId, pagination);
    return {
      ...page,
      items: await toVideoViews(page.items, viewer, this.counters),
    };
  }
}

export class ListLikedVideosUseCase {
  constructor(
    private readonly likes: LikeRepository,
    private readonly counters: VideoCounters,
  ) {}

  async execute(
    userId: string,
    pagination: Pagination,
    viewer: Viewer,
  ): Promise<Page<VideoView>> {
    const page = await this.likes.findLikedPage(userId, pagination);
    return {
      ...page,
      items: await toVideoViews(page.items, viewer, this.counters),
    };
  }
}
