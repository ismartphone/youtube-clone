import type {
  ConversationSummary,
  Message,
  Page,
  Person,
} from './conversation.js';

export const MESSAGING_REPOSITORY = Symbol('MESSAGING_REPOSITORY');

export interface MessagingRepository {
  // Conversations de [userId], triées par dernier message (les plus récentes d'abord).
  listConversations(
    userId: string,
    pagination: { page: number; pageSize: number },
  ): Promise<Page<ConversationSummary>>;
  // La conversation vue par [userId] ; null s'il n'en est pas participant (ou si elle n'existe pas).
  findConversation(
    conversationId: string,
    userId: string,
  ): Promise<ConversationSummary | null>;
  // La conversation de la paire, créée si elle n'existe pas. null si l'autre n'existe pas.
  findOrCreate(userAId: string, userBId: string): Promise<string | null>;
  // Identifiant de l'autre participant ; null si [userId] n'est pas participant.
  otherParticipant(
    conversationId: string,
    userId: string,
  ): Promise<string | null>;
  // Du plus récent au plus ancien.
  listMessages(
    conversationId: string,
    pagination: { page: number; pageSize: number },
  ): Promise<Page<Message>>;
  // Enregistre le message et avance la date du dernier message de la conversation.
  addMessage(
    conversationId: string,
    senderId: string,
    content: string,
  ): Promise<Message>;
  markRead(conversationId: string, userId: string, at: Date): Promise<void>;
  unreadCount(userId: string): Promise<number>;
  findPerson(userId: string): Promise<Person | null>;
  // Recherche sur le nom affiché, [userId] exclu.
  searchPeople(query: string, userId: string, limit: number): Promise<Person[]>;
}
