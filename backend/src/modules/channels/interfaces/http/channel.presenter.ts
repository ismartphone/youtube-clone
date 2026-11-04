import type { ChannelView } from '../../../videos/application/get-channel.use-case.js';
import { presentVideoSummary } from '../../../videos/interfaces/http/video.presenter.js';
import type {
  ChannelSummary,
  FollowStatus,
  ViewerChannelStatus,
} from '../../domain/channel.js';

// Forme exacte des réponses : chaque champ exposé est listé ici.

export function presentChannelSummary(channel: ChannelSummary) {
  return {
    id: channel.id,
    name: channel.name,
    handle: channel.handle,
    avatarUrl: channel.avatarUrl,
    bannerUrl: channel.bannerUrl,
  };
}

export function presentFollowStatus(status: FollowStatus) {
  return {
    followerCount: status.followerCount,
    isFollowing: status.isFollowing,
    notificationLevel: status.notificationLevel,
  };
}

export function presentChannelPage(
  channel: ChannelView,
  status: ViewerChannelStatus,
) {
  return {
    ...presentChannelSummary(channel),
    description: channel.description,
    createdAt: channel.createdAt,
    ...presentFollowStatus(status),
    isOwner: status.isOwner,
    videos: channel.videos.map(presentVideoSummary),
  };
}
