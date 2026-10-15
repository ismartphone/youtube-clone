import type { VideoRoomPublisher } from '../../realtime/domain/video-room-publisher.js';
import type { VideoCounters } from '../domain/counters.js';
import type { LikeRepository } from '../domain/like.repository.js';
import type { VideoRepository } from '../domain/video.repository.js';
import { currentCounts } from './video-view.js';

export interface LikeStatus {
  likeCount: number;
  isLiked: boolean;
}

// Aimer (like = true) ou retirer son « j'aime » (like = false).
// Le nouveau total est diffusé aux spectateurs de la vidéo (likes:update).
export class LikeVideoUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly likes: LikeRepository,
    private readonly counters: VideoCounters,
    private readonly publisher: VideoRoomPublisher,
  ) {}

  // Renvoie null si la vidéo n'existe pas.
  async execute(
    userId: string,
    videoId: string,
    like: boolean,
  ): Promise<LikeStatus | null> {
    const stored = await this.videos.findCounts(videoId);
    if (!stored) {
      return null;
    }
    const changed = like
      ? await this.likes.add(userId, videoId)
      : await this.likes.remove(userId, videoId);
    if (changed) {
      await this.counters.likes.increment(videoId, like ? 1 : -1);
    }
    const { likeCount } = await currentCounts(videoId, stored, this.counters);
    if (changed) {
      this.publisher.publish(videoId, 'likes:update', { videoId, likeCount });
    }
    return { likeCount, isLiked: like };
  }
}

export class GetLikeStatusUseCase {
  constructor(private readonly likes: LikeRepository) {}

  // Sans userId, c'est un visiteur : il n'a rien aimé.
  async execute(videoId: string, userId?: string): Promise<boolean> {
    return userId ? this.likes.isLiked(userId, videoId) : false;
  }
}
