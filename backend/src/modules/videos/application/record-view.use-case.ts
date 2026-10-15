import type { VideoRoomPublisher } from '../../realtime/domain/video-room-publisher.js';
import type { VideoCounters } from '../domain/counters.js';
import type { VideoRepository } from '../domain/video.repository.js';
import { currentCounts } from './video-view.js';

export class RecordViewUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly counters: VideoCounters,
    private readonly publisher: VideoRoomPublisher,
  ) {}

  // Renvoie false si la vidéo n'existe pas, pour ne pas compter de vues fantômes.
  // Le nouveau total est diffusé aux spectateurs de la vidéo (views:update).
  async execute(videoId: string): Promise<boolean> {
    const stored = await this.videos.findCounts(videoId);
    if (!stored) {
      return false;
    }
    await this.counters.views.increment(videoId);
    const { viewCount } = await currentCounts(videoId, stored, this.counters);
    this.publisher.publish(videoId, 'views:update', { videoId, viewCount });
    return true;
  }
}
