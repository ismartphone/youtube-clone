import { canAccessVideo } from '../domain/visibility.js';
import type { VideoRoomPublisher } from '../../realtime/domain/video-room-publisher.js';
import type { VideoCounters } from '../domain/counters.js';
import {
  likeDelta,
  type LikeRepository,
  type Reaction,
} from '../domain/like.repository.js';
import type { VideoRepository } from '../domain/video.repository.js';
import { currentCounts } from './video-view.js';

export interface LikeStatus {
  likeCount: number;
  // Avis de l'utilisateur après l'opération, ou null s'il n'en a plus.
  reaction: Reaction | null;
}

/**
 * Donne (on = true) ou retire (on = false) un « J'aime » ou un « Je n'aime pas » (ADR-019). Les
 * deux s'excluent : en donner un remplace l'autre. Retirer un avis qu'on n'a pas donné ne change
 * rien. Quand le nombre de « j'aime » change, il est diffusé aux spectateurs (likes:update) ; les
 * avis négatifs ne sont jamais comptés ni diffusés.
 */
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
    reaction: Reaction,
    on: boolean,
  ): Promise<LikeStatus | null> {
    // Vidéo privée d'un autre : comme si elle n'existait pas (ADR-016).
    const access = await this.videos.findAccess(videoId);
    if (!access || !canAccessVideo(access.visibility, access.ownerId, userId)) {
      return null;
    }
    const stored = await this.videos.findCounts(videoId);
    if (!stored) {
      return null;
    }
    let next: Reaction | null;
    let delta = 0;
    if (on) {
      const previous = await this.likes.react(userId, videoId, reaction);
      next = reaction;
      delta = likeDelta(previous, next);
    } else if ((await this.likes.reactionOf(userId, videoId)) === reaction) {
      await this.likes.react(userId, videoId, null);
      next = null;
      delta = likeDelta(reaction, null);
    } else {
      next = await this.likes.reactionOf(userId, videoId);
    }
    if (delta !== 0) {
      await this.counters.likes.increment(videoId, delta);
    }
    const { likeCount } = await currentCounts(videoId, stored, this.counters);
    if (delta !== 0) {
      this.publisher.publish(videoId, 'likes:update', { videoId, likeCount });
    }
    return { likeCount, reaction: next };
  }
}

export class GetLikeStatusUseCase {
  constructor(private readonly likes: LikeRepository) {}

  // Sans userId, c'est un visiteur : il n'a donné aucun avis.
  async execute(videoId: string, userId?: string): Promise<Reaction | null> {
    return userId ? this.likes.reactionOf(userId, videoId) : null;
  }
}
