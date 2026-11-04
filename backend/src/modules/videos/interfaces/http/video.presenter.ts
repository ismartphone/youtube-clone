import type { Reaction } from '../../domain/like.repository.js';
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
    visibility: video.visibility,
    commentsEnabled: video.commentsEnabled,
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

// isLiked et isDisliked ne sont présents que sur la route publique : ils dépendent de l'utilisateur
// de la requête. Le nombre de « Je n'aime pas » n'est jamais renvoyé (ADR-019).
export function presentVideoDetail(
  video: VideoView,
  reaction?: Reaction | null,
) {
  return {
    ...presentVideoSummary(video),
    description: video.description,
    videoUrl: video.videoUrl,
    hlsUrl: video.hlsUrl,
    subtitleUrl: video.subtitleUrl,
    likeCount: video.likeCount,
    ...(reaction === undefined ? {} : presentReaction(reaction)),
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

export function presentReaction(reaction: Reaction | null) {
  return { isLiked: reaction === 'LIKE', isDisliked: reaction === 'DISLIKE' };
}
