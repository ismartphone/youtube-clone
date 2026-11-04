// Notifications de publication (ADR-020), indépendantes de Prisma, de NestJS et de Firebase.

import type { NotificationLevel } from '../../channels/domain/channel.js';
import type { VideoFormat } from '../../videos/domain/video.js';

// Vidéo publique à annoncer, réservée par PublicationRepository.claim : elle ne notifiera plus.
export interface Publication {
  videoId: string;
  title: string;
  format: VideoFormat;
  thumbnailUrl: string | null;
  channelId: string;
  channelName: string;
  ownerId: string;
}

// Ce que reçoit l'appareil. Message de données seulement : l'application construit elle-même la
// notification, avec la miniature résolue sur l'adresse du serveur qu'elle connaît.
export interface PublicationMessage {
  videoId: string;
  channelName: string;
  title: string;
  thumbnailUrl: string | null;
}

/**
 * Cloches qui reçoivent une publication : Toutes pour tout format, Personnalisées pour les vidéos
 * classiques seulement. Chez nous, « Personnalisées » veut dire « sans les Shorts ».
 */
export function levelsNotifiedFor(format: VideoFormat): NotificationLevel[] {
  return format === 'LONG' ? ['ALL', 'PERSONALIZED'] : ['ALL'];
}

export function toMessage(publication: Publication): PublicationMessage {
  return {
    videoId: publication.videoId,
    channelName: publication.channelName,
    title: publication.title,
    thumbnailUrl: publication.thumbnailUrl,
  };
}
