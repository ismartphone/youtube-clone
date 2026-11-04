// Modèles du domaine de la messagerie (ADR-025), indépendants de Prisma et de NestJS.

import type { VideoFormat } from '../../videos/domain/video.js';

// Personne telle qu'affichée dans la messagerie : jamais son email. [handle] : @handle de sa
// chaîne, pour départager les homonymes ; null sans chaîne.
export interface Person {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  handle: string | null;
}

/**
 * Vidéo partagée dans un message (ADR-025, étape 2), construite à chaque lecture, pour celui qui
 * lit, et jamais figée au moment du partage. Supprimée depuis, ou privée pour lui : indisponible,
 * sans titre ni miniature, pour ne rien révéler (ADR-016). Une vidéo Premium reste visible :
 * l'accès est vérifié à l'ouverture (ADR-007).
 */
export type SharedVideo =
  | {
      available: true;
      id: string;
      title: string;
      thumbnailUrl: string | null;
      channelName: string;
      format: VideoFormat;
      isPremium: boolean;
    }
  | { available: false };

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  // Vide quand une vidéo est partagée sans texte.
  content: string;
  createdAt: Date;
  // null pour un message sans vidéo.
  video: SharedVideo | null;
}

// Conversation vue par l'un de ses participants.
export interface ConversationSummary {
  id: string;
  // L'autre participant.
  other: Person;
  lastMessage: Message | null;
  // Messages de l'autre arrivés après la dernière lecture.
  unreadCount: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

// Message : 2000 caractères au plus (ADR-025, décision 5).
export const MESSAGE_MAX_LENGTH = 2000;

// Conversation avec soi-même : refusée.
export class SelfConversationError extends Error {
  constructor() {
    super('Impossible de démarrer une conversation avec soi-même.');
  }
}

// Une seule conversation par paire : les deux identifiants sont rangés dans l'ordre, le plus
// petit en A (ADR-025).
export function orderedPair(
  first: string,
  second: string,
): { userAId: string; userBId: string } {
  if (first === second) {
    throw new SelfConversationError();
  }
  return first < second
    ? { userAId: first, userBId: second }
    : { userAId: second, userBId: first };
}
