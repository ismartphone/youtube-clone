import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type {
  ConversationSummary,
  Message,
  Page,
  Person,
} from '../domain/conversation.js';
import type { MessagingRepository } from '../domain/ports.js';

// Sélection explicite : d'un utilisateur, seuls son nom, son avatar et le @handle de sa chaîne
// sortent de la base, jamais son email.
const PERSON_SELECT = {
  id: true,
  displayName: true,
  avatarUrl: true,
  channel: { select: { handle: true, avatarUrl: true } },
} satisfies Prisma.UserSelect;

type PersonRow = Prisma.UserGetPayload<{ select: typeof PERSON_SELECT }>;

// Comme l'en-tête de Vous : la photo de la chaîne d'abord, sinon celle du compte.
function toPerson(row: PersonRow): Person {
  return {
    id: row.id,
    displayName: row.displayName,
    avatarUrl: row.channel?.avatarUrl ?? row.avatarUrl,
    handle: row.channel?.handle ?? null,
  };
}

const MESSAGE_SELECT = {
  id: true,
  conversationId: true,
  senderId: true,
  content: true,
  createdAt: true,
} satisfies Prisma.MessageSelect;

const CONVERSATION_SELECT = {
  id: true,
  userAId: true,
  readAtA: true,
  readAtB: true,
  userA: { select: PERSON_SELECT },
  userB: { select: PERSON_SELECT },
  messages: {
    select: MESSAGE_SELECT,
    orderBy: { createdAt: 'desc' },
    take: 1,
  },
} satisfies Prisma.ConversationSelect;

type ConversationRow = Prisma.ConversationGetPayload<{
  select: typeof CONVERSATION_SELECT;
}>;

// Conversations où [userId] est l'un des deux participants.
const participant = (userId: string): Prisma.ConversationWhereInput => ({
  OR: [{ userAId: userId }, { userBId: userId }],
});

export class PrismaMessagingRepository implements MessagingRepository {
  constructor(private readonly prisma: PrismaService) {}

  // Les conversations encore vides (démarrées sans message) ne sont pas listées.
  async listConversations(
    userId: string,
    { page, pageSize }: { page: number; pageSize: number },
  ): Promise<Page<ConversationSummary>> {
    const where = { ...participant(userId), lastMessageAt: { not: null } };
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.conversation.findMany({
        where,
        select: CONVERSATION_SELECT,
        orderBy: [{ lastMessageAt: 'desc' }, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.conversation.count({ where }),
    ]);
    const items = await Promise.all(
      rows.map((row) => this.toSummary(row, userId)),
    );
    return { items, page, pageSize, total };
  }

  async findConversation(
    conversationId: string,
    userId: string,
  ): Promise<ConversationSummary | null> {
    const row = await this.prisma.conversation.findFirst({
      where: { id: conversationId, ...participant(userId) },
      select: CONVERSATION_SELECT,
    });
    return row && this.toSummary(row, userId);
  }

  async findOrCreate(userAId: string, userBId: string): Promise<string | null> {
    const users = await this.prisma.user.count({
      where: { id: { in: [userAId, userBId] } },
    });
    if (users < 2) {
      return null;
    }
    const conversation = await this.prisma.conversation.upsert({
      where: { userAId_userBId: { userAId, userBId } },
      create: { userAId, userBId },
      update: {},
      select: { id: true },
    });
    return conversation.id;
  }

  async otherParticipant(
    conversationId: string,
    userId: string,
  ): Promise<string | null> {
    const row = await this.prisma.conversation.findFirst({
      where: { id: conversationId, ...participant(userId) },
      select: { userAId: true, userBId: true },
    });
    if (!row) {
      return null;
    }
    return row.userAId === userId ? row.userBId : row.userAId;
  }

  // Index (conversationId, createdAt DESC) prévu dans le schéma pour cette requête.
  async listMessages(
    conversationId: string,
    { page, pageSize }: { page: number; pageSize: number },
  ): Promise<Page<Message>> {
    const [items, total] = await this.prisma.$transaction([
      this.prisma.message.findMany({
        where: { conversationId },
        select: MESSAGE_SELECT,
        orderBy: [{ createdAt: 'desc' }, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.message.count({ where: { conversationId } }),
    ]);
    return { items, page, pageSize, total };
  }

  async addMessage(
    conversationId: string,
    senderId: string,
    content: string,
  ): Promise<Message> {
    return this.prisma.$transaction(async (tx) => {
      const created = await tx.message.create({
        data: { conversationId, senderId, content },
        select: MESSAGE_SELECT,
      });
      await tx.conversation.update({
        where: { id: conversationId },
        data: { lastMessageAt: created.createdAt },
      });
      return created;
    });
  }

  async markRead(conversationId: string, userId: string, at: Date) {
    const row = await this.prisma.conversation.findUnique({
      where: { id: conversationId },
      select: { userAId: true },
    });
    if (!row) {
      return;
    }
    await this.prisma.conversation.update({
      where: { id: conversationId },
      data: row.userAId === userId ? { readAtA: at } : { readAtB: at },
    });
  }

  // Petit volume (projet d'école) : un comptage par conversation suffit.
  async unreadCount(userId: string): Promise<number> {
    const rows = await this.prisma.conversation.findMany({
      where: { ...participant(userId), lastMessageAt: { not: null } },
      select: { id: true, userAId: true, readAtA: true, readAtB: true },
    });
    const counts = await Promise.all(
      rows.map((row) => this.countUnread(row, userId)),
    );
    return counts.reduce((sum, count) => sum + count, 0);
  }

  async findPerson(userId: string): Promise<Person | null> {
    const row = await this.prisma.user.findUnique({
      where: { id: userId },
      select: PERSON_SELECT,
    });
    return row && toPerson(row);
  }

  async searchPeople(
    query: string,
    userId: string,
    limit: number,
  ): Promise<Person[]> {
    const rows = await this.prisma.user.findMany({
      where: {
        id: { not: userId },
        displayName: { contains: query, mode: 'insensitive' },
      },
      select: PERSON_SELECT,
      orderBy: [{ displayName: 'asc' }, { id: 'asc' }],
      take: limit,
    });
    return rows.map(toPerson);
  }

  private async toSummary(
    row: ConversationRow,
    userId: string,
  ): Promise<ConversationSummary> {
    const isA = row.userAId === userId;
    return {
      id: row.id,
      other: toPerson(isA ? row.userB : row.userA),
      lastMessage: row.messages[0] ?? null,
      unreadCount: await this.countUnread(row, userId),
    };
  }

  // Messages de l'autre arrivés après la dernière lecture de [userId].
  private countUnread(
    row: {
      id: string;
      userAId: string;
      readAtA: Date | null;
      readAtB: Date | null;
    },
    userId: string,
  ): Promise<number> {
    const readAt = row.userAId === userId ? row.readAtA : row.readAtB;
    return this.prisma.message.count({
      where: {
        conversationId: row.id,
        senderId: { not: userId },
        ...(readAt ? { createdAt: { gt: readAt } } : {}),
      },
    });
  }
}
