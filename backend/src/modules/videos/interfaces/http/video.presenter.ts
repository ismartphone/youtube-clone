import type { ChannelView } from '../../application/get-channel.use-case.js';
import type { VideoView } from '../../application/video-view.js';

// Forme exacte des réponses HTTP : chaque champ exposé est listé ici, rien n'est recopié en bloc.

export function presentVideoSummary(video: VideoView) {
  return {
    id: video.id,
    title: video.title,
    thumbnailUrl: video.thumbnailUrl,
    durationSeconds: video.durationSeconds,
    format: video.format,
    isPremium: video.isPremium,
    viewCount: video.viewCount,
    publishedAt: video.publishedAt,
    channel: {
      id: video.channel.id,
      name: video.channel.name,
      handle: video.channel.handle,
      avatarUrl: video.channel.avatarUrl,
    },
    category: video.category && {
      id: video.category.id,
      name: video.category.name,
    },
  };
}

// isLiked n'est présent que sur la route publique : il dépend de l'utilisateur de la requête.
export function presentVideoDetail(video: VideoView, isLiked?: boolean) {
  return {
    ...presentVideoSummary(video),
    description: video.description,
    videoUrl: video.videoUrl,
    likeCount: video.likeCount,
    ...(isLiked === undefined ? {} : { isLiked }),
  };
}

export function presentChannel(channel: ChannelView) {
  return {
    id: channel.id,
    name: channel.name,
    handle: channel.handle,
    description: channel.description,
    avatarUrl: channel.avatarUrl,
    createdAt: channel.createdAt,
    videos: channel.videos.map(presentVideoSummary),
  };
}
