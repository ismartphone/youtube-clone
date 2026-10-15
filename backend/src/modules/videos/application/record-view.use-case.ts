import type { VideoRepository } from '../domain/video.repository.js';
import type { ViewCounter } from '../domain/view-counter.js';

export class RecordViewUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly viewCounter: ViewCounter,
  ) {}

  // Renvoie false si la vidéo n'existe pas, pour ne pas compter de vues fantômes.
  async execute(videoId: string): Promise<boolean> {
    if (!(await this.videos.exists(videoId))) {
      return false;
    }
    await this.viewCounter.increment(videoId);
    return true;
  }
}
