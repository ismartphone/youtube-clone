import type { MessagingPublisher } from '../../realtime/domain/messaging-publisher.js';
import {
  orderedPair,
  type ConversationSummary,
  type Message,
  type Page,
  type Person,
} from '../domain/conversation.js';
import type { MessagingRepository } from '../domain/ports.js';

// Message tel que diffusé en direct (message:new) : avec son expéditeur, pour que la liste
// des conversations affiche aussi une conversation que l'autre vient de démarrer.
export type DeliveredMessage = Message & { sender: Person };

// Cas d'usage de la messagerie (ADR-025, étape 1). Un tiers ne voit jamais une conversation :
// pour lui, elle n'existe pas (null, donc 404), comme une vidéo privée (ADR-016).
export class MessagingUseCases {
  constructor(
    private readonly repository: MessagingRepository,
    private readonly publisher: MessagingPublisher,
  ) {}

  listConversations(
    userId: string,
    pagination: { page: number; pageSize: number },
  ): Promise<Page<ConversationSummary>> {
    return this.repository.listConversations(userId, pagination);
  }

  // Conversation existante avec [otherId], ou nouvelle. null si l'autre n'existe pas.
  // Lève SelfConversationError pour une conversation avec soi-même.
  async start(
    userId: string,
    otherId: string,
  ): Promise<ConversationSummary | null> {
    const pair = orderedPair(userId, otherId);
    const id = await this.repository.findOrCreate(pair.userAId, pair.userBId);
    return id ? this.repository.findConversation(id, userId) : null;
  }

  getConversation(
    conversationId: string,
    userId: string,
  ): Promise<ConversationSummary | null> {
    return this.repository.findConversation(conversationId, userId);
  }

  async listMessages(
    conversationId: string,
    userId: string,
    pagination: { page: number; pageSize: number },
  ): Promise<Page<Message> | null> {
    if (!(await this.repository.otherParticipant(conversationId, userId))) {
      return null;
    }
    return this.repository.listMessages(conversationId, pagination);
  }

  // Enregistre le message, puis le diffuse dans la salle de la conversation (écran ouvert) et
  // dans la salle personnelle du destinataire (liste des conversations ouverte).
  async send(
    conversationId: string,
    senderId: string,
    content: string,
  ): Promise<Message | null> {
    const recipientId = await this.repository.otherParticipant(
      conversationId,
      senderId,
    );
    if (!recipientId) {
      return null;
    }
    const message = await this.repository.addMessage(
      conversationId,
      senderId,
      content.trim(),
    );
    // L'expéditeur a forcément lu jusqu'à son propre message.
    await this.repository.markRead(conversationId, senderId, message.createdAt);
    const sender = await this.repository.findPerson(senderId);
    if (sender) {
      const delivered: DeliveredMessage = { ...message, sender };
      this.publisher.toConversation(conversationId, 'message:new', delivered);
      this.publisher.toUser(recipientId, 'message:new', delivered);
    }
    return message;
  }

  // Marquage comme lu à l'ouverture de la conversation. false si elle n'existe pas pour lui.
  async markRead(conversationId: string, userId: string): Promise<boolean> {
    if (!(await this.repository.otherParticipant(conversationId, userId))) {
      return false;
    }
    await this.repository.markRead(conversationId, userId, new Date());
    return true;
  }

  unreadCount(userId: string): Promise<number> {
    return this.repository.unreadCount(userId);
  }

  searchPeople(query: string, userId: string): Promise<Person[]> {
    return this.repository.searchPeople(query.trim(), userId, SEARCH_LIMIT);
  }
}

const SEARCH_LIMIT = 20;
