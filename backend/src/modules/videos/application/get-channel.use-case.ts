import type { Channel, Viewer } from '../domain/video.js';
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
    return {
      ...channel,
      videos: await toVideoViews(channel.videos, viewer, this.counters),
    };
  }
}
