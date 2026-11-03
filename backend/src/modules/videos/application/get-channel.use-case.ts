import type { Channel, Viewer } from '../domain/video.js';
import { isListedFor } from '../domain/visibility.js';
import type { VideoRepository } from '../domain/video.repository.js';
import type { VideoCounters } from '../domain/counters.js';
import { toVideoViews, type VideoView } from './video-view.js';

export type ChannelView = Omit<Channel, 'videos'> & { videos: VideoView[] };

export class GetChannelUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly counters: VideoCounters,
  ) {}

  async execute(
    channelId: string,
    viewer: Viewer,
  ): Promise<ChannelView | null> {
    const channel = await this.videos.findChannelWithVideos(channelId);
    if (!channel) {
      return null;
    }
    // Le propriétaire voit toutes ses vidéos (avec leur état) ; les autres, les publiques.
    const listed = channel.videos.filter(
      (video) =>
        viewer.isAdmin === true ||
        isListedFor(video.visibility, channel.ownerId, viewer.userId),
    );
    return {
      ...channel,
      videos: await toVideoViews(listed, viewer, this.counters),
    };
  }
}
