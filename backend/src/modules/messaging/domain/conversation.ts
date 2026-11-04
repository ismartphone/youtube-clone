// Modèles du domaine de la messagerie (ADR-025), indépendants de Prisma et de NestJS.

// Personne telle qu'affichée dans la messagerie : jamais son email. [handle] : @handle de sa
// chaîne, pour départager les homonymes ; null sans chaîne.
export interface Person {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  handle: string | null;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  createdAt: Date;
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
