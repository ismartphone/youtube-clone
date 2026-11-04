import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { canAccessVideo } from '../../videos/domain/visibility.js';
import type {
  ConversationSummary,
  Message,
  Page,
  Person,
  SharedVideo,
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
  videoId: true,
  createdAt: true,
} satisfies Prisma.MessageSelect;

type MessageRow = Prisma.MessageGetPayload<{ select: typeof MESSAGE_SELECT }>;

const SHARED_VIDEO_SELECT = {
  id: true,
  title: true,
  thumbnailUrl: true,
  format: true,
  isPremium: true,
  visibility: true,
  channel: { select: { name: true, ownerId: true } },
} satisfies Prisma.VideoSelect;

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
    viewerId: string,
  ): Promise<Page<Message>> {
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.message.findMany({
        where: { conversationId },
        select: MESSAGE_SELECT,
        orderBy: [{ createdAt: 'desc' }, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      this.prisma.message.count({ where: { conversationId } }),
    ]);
    return {
      items: await this.withVideos(rows, viewerId),
      page,
      pageSize,
      total,
    };
  }

  async addMessage(
    conversationId: string,
    senderId: string,
    content: string,
    videoId: string | null,
    viewerId: string,
  ): Promise<Message> {
    const created = await this.prisma.$transaction(async (tx) => {
      const row = await tx.message.create({
        data: { conversationId, senderId, content, videoId },
        select: MESSAGE_SELECT,
      });
      await tx.conversation.update({
        where: { id: conversationId },
        data: { lastMessageAt: row.createdAt },
      });
      return row;
    });
    const [message] = await this.withVideos([created], viewerId);
    return message;
  }

  async findMessage(
    messageId: string,
    viewerId: string,
  ): Promise<Message | null> {
    const row = await this.prisma.message.findUnique({
      where: { id: messageId },
      select: MESSAGE_SELECT,
    });
    return row && (await this.withVideos([row], viewerId))[0];
  }

  async canShareVideo(videoId: string, userId: string): Promise<boolean> {
    const video = await this.prisma.video.findUnique({
      where: { id: videoId },
      select: { visibility: true, channel: { select: { ownerId: true } } },
    });
    return (
      video !== null &&
      canAccessVideo(video.visibility, video.channel.ownerId, userId)
    );
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

  // Vidéos partagées des messages, en une requête, vues par [viewerId] (ADR-025, étape 2).
  private async withVideos(
    rows: MessageRow[],
    viewerId: string,
  ): Promise<Message[]> {
    const ids = [
      ...new Set(rows.flatMap((row) => (row.videoId ? [row.videoId] : []))),
    ];
    const videos = ids.length
      ? await this.prisma.video.findMany({
          where: { id: { in: ids } },
          select: SHARED_VIDEO_SELECT,
        })
      : [];
    const byId = new Map(videos.map((video) => [video.id, video]));
    return rows.map(({ videoId, ...message }) => ({
      ...message,
      video: videoId ? toSharedVideo(byId.get(videoId), viewerId) : null,
    }));
  }

  private async toSummary(
    row: ConversationRow,
    userId: string,
  ): Promise<ConversationSummary> {
    const isA = row.userAId === userId;
    const [lastMessage] = await this.withVideos(row.messages, userId);
    return {
      id: row.id,
      other: toPerson(isA ? row.userB : row.userA),
      lastMessage: lastMessage ?? null,
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

// Supprimée, ou privée pour celui qui lit : indisponible, sans rien révéler (ADR-016).
function toSharedVideo(
  video:
    Prisma.VideoGetPayload<{ select: typeof SHARED_VIDEO_SELECT }> | undefined,
  viewerId: string,
): SharedVideo {
  if (
    !video ||
    !canAccessVideo(video.visibility, video.channel.ownerId, viewerId)
  ) {
    return { available: false };
  }
  return {
    available: true,
    id: video.id,
    title: video.title,
    thumbnailUrl: video.thumbnailUrl,
    channelName: video.channel.name,
    format: video.format,
    isPremium: video.isPremium,
  };
}
