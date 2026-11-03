import type { VideoCounters } from '../domain/counters.js';
import { playableUrl, type Video, type Viewer } from '../domain/video.js';
import type { StoredCounts } from '../domain/video.repository.js';

// Vidéo telle que la voit un spectateur : URL masquée si l'accès est refusé,
// compteurs incluant les variations pas encore reportées en base.
export type VideoView = Omit<Video, 'videoUrl'> & { videoUrl: string | null };
// hlsUrl suit la même règle que videoUrl : masqué quand l'accès au fichier est refusé.

export async function toVideoViews(
  videos: Video[],
  viewer: Viewer,
  counters: VideoCounters,
): Promise<VideoView[]> {
  const ids = videos.map((v) => v.id);
  const [pendingViews, pendingLikes] = await Promise.all([
    counters.views.pendingCounts(ids),
    counters.likes.pendingCounts(ids),
  ]);
  return videos.map((video) => ({
    ...video,
    videoUrl: playableUrl(video, viewer),
    hlsUrl: playableUrl(video, viewer) === null ? null : video.hlsUrl,
    viewCount: video.viewCount + (pendingViews.get(video.id) ?? 0),
    likeCount: video.likeCount + (pendingLikes.get(video.id) ?? 0),
  }));
}

// Compteurs à jour d'une vidéo, pour la diffusion en temps réel.
export async function currentCounts(
  videoId: string,
  stored: StoredCounts,
  counters: VideoCounters,
): Promise<StoredCounts> {
  const [pendingViews, pendingLikes] = await Promise.all([
    counters.views.pendingCounts([videoId]),
    counters.likes.pendingCounts([videoId]),
  ]);
  return {
    viewCount: stored.viewCount + (pendingViews.get(videoId) ?? 0),
    likeCount: stored.likeCount + (pendingLikes.get(videoId) ?? 0),
  };
}
