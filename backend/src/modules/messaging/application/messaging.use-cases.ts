import type { MessageNotifier } from '../../notifications/domain/message-notifier.js';
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
    private readonly notifier: MessageNotifier,
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
    return this.repository.listMessages(conversationId, pagination, userId);
  }

  /**
   * Enregistre le message, texte ou vidéo partagée (ADR-025, étape 2), puis le diffuse dans la
   * salle de la conversation (écran ouvert) et dans la salle personnelle du destinataire (liste
   * des conversations ouverte), et le notifie (étape 3). Renvoie null si la conversation, ou la
   * vidéo pour l'expéditeur, n'existe pas.
   */
  async send(
    conversationId: string,
    senderId: string,
    content: string,
    videoId: string | null = null,
  ): Promise<Message | null> {
    const recipientId = await this.repository.otherParticipant(
      conversationId,
      senderId,
    );
    if (!recipientId) {
      return null;
    }
    if (videoId && !(await this.repository.canShareVideo(videoId, senderId))) {
      return null;
    }
    const message = await this.repository.addMessage(
      conversationId,
      senderId,
      content.trim(),
      videoId,
      senderId,
    );
    // L'expéditeur a forcément lu jusqu'à son propre message.
    await this.repository.markRead(conversationId, senderId, message.createdAt);
    const sender = await this.repository.findPerson(senderId);
    // La vidéo est vue par le destinataire : privée, elle lui est indisponible.
    const received = await this.repository.findMessage(message.id, recipientId);
    if (sender && received) {
      const delivered: DeliveredMessage = { ...received, sender };
      this.publisher.toConversation(conversationId, 'message:new', delivered);
      this.publisher.toUser(recipientId, 'message:new', delivered);
      this.notifier.notify(recipientId, {
        conversationId,
        senderName: sender.displayName,
        text: message.content || null,
      });
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
