// Modèles et règles du domaine des lives (ADR-029), indépendants de Prisma et de NestJS.

import type { VideoFormat } from '../../videos/domain/video.js';

// Seuil d'abonnés (FOLLOW) pour démarrer un live : même règle que l'écran de l'ADR-015, mais
// gardée ici par le serveur, pas seulement par l'application.
export const MIN_FOLLOWERS_FOR_LIVE = 50;
export const LIVE_TITLE_MAX_LENGTH = 100;
// Chat (ADR-029, D5) : messages courts, un par seconde et par compte, 50 lus à l'arrivée.
export const CHAT_MESSAGE_MAX_LENGTH = 300;
export const CHAT_MIN_INTERVAL_MS = 1000;
export const CHAT_HISTORY_SIZE = 50;

export type LiveStatus = 'LIVE' | 'ENDED';
export type VideoVisibility = 'PUBLIC' | 'UNLISTED' | 'PRIVATE';

// Vidéo vue sous l'angle du flux d'un live.
export interface StreamCandidate {
  channelId: string;
  visibility: VideoVisibility;
  isPremium: boolean;
}

/**
 * Une vidéo peut servir de flux si elle est de la chaîne du diffuseur, publique et non Premium
 * (ADR-029, D1). Sans cela, un live ferait lire à tous une vidéo privée (ADR-016) ou exclusive
 * (ADR-007) : le serveur ne tiendrait plus seul l'accès.
 */
export function canStreamVideo(
  video: StreamCandidate | null,
  channelId: string,
): boolean {
  return (
    video !== null &&
    video.channelId === channelId &&
    video.visibility === 'PUBLIC' &&
    !video.isPremium
  );
}

export interface LiveChannel {
  id: string;
  name: string;
  handle: string;
  avatarUrl: string | null;
  ownerId: string;
}

// Fichier lu en boucle comme flux, avec de quoi vérifier qu'il peut toujours servir de flux
// (fin paresseuse du live, D1).
export interface LiveVideo extends StreamCandidate {
  id: string;
  videoUrl: string;
  hlsUrl: string | null;
  thumbnailUrl: string;
  durationSeconds: number;
  format: VideoFormat;
}

// Live tel que stocké, avec sa chaîne et sa vidéo (null si elle a été supprimée).
export interface LiveRecord {
  id: string;
  title: string;
  status: LiveStatus;
  startedAt: Date;
  endedAt: Date | null;
  channel: LiveChannel;
  video: LiveVideo | null;
}

// Live tel que vu par un client : avec son nombre de spectateurs, sans détail interne.
export interface LiveView {
  id: string;
  title: string;
  status: LiveStatus;
  startedAt: Date;
  endedAt: Date | null;
  viewerCount: number;
  channel: Omit<LiveChannel, 'ownerId'>;
  video: Pick<
    LiveVideo,
    'id' | 'videoUrl' | 'hlsUrl' | 'thumbnailUrl' | 'durationSeconds' | 'format'
  > | null;
}

export function toLiveView(record: LiveRecord, viewerCount: number): LiveView {
  const { ownerId: _ownerId, ...channel } = record.channel;
  return {
    id: record.id,
    title: record.title,
    status: record.status,
    startedAt: record.startedAt,
    endedAt: record.endedAt,
    viewerCount,
    channel,
    video: record.video && {
      id: record.video.id,
      videoUrl: record.video.videoUrl,
      hlsUrl: record.video.hlsUrl,
      thumbnailUrl: record.video.thumbnailUrl,
      durationSeconds: record.video.durationSeconds,
      format: record.video.format,
    },
  };
}

// Auteur d'un message : nom et avatar, jamais son email.
export interface LiveAuthor {
  id: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface LiveChatMessage {
  id: string;
  liveId: string;
  kind: 'CHAT' | 'PURCHASE';
  content: string;
  createdAt: Date;
  author: LiveAuthor;
}

export class ChannelRequiredError extends Error {
  constructor() {
    super('Créez une chaîne pour passer en direct.');
  }
}

export class NotEnoughFollowersError extends Error {
  constructor() {
    super(
      `Une chaîne doit compter au moins ${MIN_FOLLOWERS_FOR_LIVE} abonnés pour passer en direct.`,
    );
  }
}

export class LiveAlreadyRunningError extends Error {
  constructor() {
    super('Un live est déjà en cours sur votre chaîne.');
  }
}

export class VideoNotEligibleError extends Error {
  constructor() {
    super(
      'Choisissez une vidéo publique et non Premium de votre chaîne comme flux.',
    );
  }
}

export class ChatTooFastError extends Error {
  constructor() {
    super('Doucement : un message par seconde.');
  }
}

export class LiveEndedError extends Error {
  constructor() {
    super('Ce live est terminé.');
  }
}

/**
 * Limiteur de débit du chat : un message par [intervalMs] et par compte. Gardé en mémoire, comme
 * les salles : valable pour une seule instance du serveur (ADR-010).
 */
export class ChatRateLimiter {
  private readonly lastSentAt = new Map<string, number>();

  constructor(
    private readonly intervalMs: number = CHAT_MIN_INTERVAL_MS,
    private readonly now: () => number = Date.now,
  ) {}

  // Vrai (et le message est compté) si [userId] peut écrire maintenant.
  tryAcquire(userId: string): boolean {
    const now = this.now();
    const last = this.lastSentAt.get(userId);
    if (last !== undefined && now - last < this.intervalMs) {
      return false;
    }
    this.lastSentAt.set(userId, now);
    // Évite de garder indéfiniment tous les comptes déjà passés par le chat.
    if (this.lastSentAt.size > 5000) {
      for (const [id, at] of this.lastSentAt) {
        if (now - at >= this.intervalMs) this.lastSentAt.delete(id);
      }
    }
    return true;
  }
}
