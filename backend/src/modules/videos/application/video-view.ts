import { playableUrl, type Video, type Viewer } from '../domain/video.js';
import type { ViewCounter } from '../domain/view-counter.js';

// Vidéo telle que la voit un spectateur : URL masquée si l'accès est refusé,
// compteur de vues incluant celles pas encore reportées en base.
export type VideoView = Omit<Video, 'videoUrl'> & { videoUrl: string | null };

export async function toVideoViews(
  videos: Video[],
  viewer: Viewer,
  viewCounter: ViewCounter,
): Promise<VideoView[]> {
  const pending = await viewCounter.pendingCounts(videos.map((v) => v.id));
  return videos.map((video) => ({
    ...video,
    videoUrl: playableUrl(video, viewer),
    viewCount: video.viewCount + (pending.get(video.id) ?? 0),
  }));
}
