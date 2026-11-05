import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type {
  LiveAuthor,
  LiveChatMessage,
  LiveRecord,
  StreamCandidate,
} from '../domain/live.js';
import type { LivesRepository } from '../domain/ports.js';

// Sélections explicites : seuls les champs utiles sortent de la base, jamais l'email d'un compte.
const LIVE_SELECT = {
  id: true,
  title: true,
  status: true,
  startedAt: true,
  endedAt: true,
  channel: {
    select: {
      id: true,
      name: true,
      handle: true,
      avatarUrl: true,
      ownerId: true,
    },
  },
  video: {
    select: {
      id: true,
      videoUrl: true,
      hlsUrl: true,
      thumbnailUrl: true,
      durationSeconds: true,
      format: true,
      channelId: true,
      visibility: true,
      isPremium: true,
    },
  },
} satisfies Prisma.LiveSelect;

const MESSAGE_SELECT = {
  id: true,
  liveId: true,
  kind: true,
  content: true,
  createdAt: true,
  user: { select: { id: true, displayName: true, avatarUrl: true } },
} satisfies Prisma.LiveMessageSelect;

type MessageRow = Prisma.LiveMessageGetPayload<{
  select: typeof MESSAGE_SELECT;
}>;

function toMessage(row: MessageRow): LiveChatMessage {
  return {
    id: row.id,
    liveId: row.liveId,
    kind: row.kind,
    content: row.content,
    createdAt: row.createdAt,
    author: row.user,
  };
}

export class PrismaLivesRepository implements LivesRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findOwnChannelId(userId: string): Promise<string | null> {
    const channel = await this.prisma.channel.findUnique({
      where: { ownerId: userId },
      select: { id: true },
    });
    return channel?.id ?? null;
  }

  countFollowers(channelId: string): Promise<number> {
    return this.prisma.follow.count({ where: { channelId } });
  }

  findStreamCandidate(videoId: string): Promise<StreamCandidate | null> {
    return this.prisma.video.findUnique({
      where: { id: videoId },
      select: { channelId: true, visibility: true, isPremium: true },
    });
  }

  createIfNoneRunning(
    channelId: string,
    title: string,
    videoId: string,
  ): Promise<LiveRecord | null> {
    return this.prisma.$transaction(async (tx) => {
      const running = await tx.live.count({
        where: { channelId, status: 'LIVE' },
      });
      if (running > 0) {
        return null;
      }
      return tx.live.create({
        data: { channelId, title, videoId },
        select: LIVE_SELECT,
      });
    });
  }

  find(liveId: string): Promise<LiveRecord | null> {
    return this.prisma.live.findUnique({
      where: { id: liveId },
      select: LIVE_SELECT,
    });
  }

  listRunning(channelId?: string): Promise<LiveRecord[]> {
    return this.prisma.live.findMany({
      where: { status: 'LIVE', ...(channelId ? { channelId } : {}) },
      orderBy: { startedAt: 'desc' },
      select: LIVE_SELECT,
    });
  }

  async end(liveId: string, at: Date): Promise<boolean> {
    const { count } = await this.prisma.live.updateMany({
      where: { id: liveId, status: 'LIVE' },
      data: { status: 'ENDED', endedAt: at },
    });
    return count > 0;
  }

  async listMessages(
    liveId: string,
    limit: number,
  ): Promise<LiveChatMessage[]> {
    const rows = await this.prisma.liveMessage.findMany({
      where: { liveId },
      orderBy: { createdAt: 'desc' },
      take: limit,
      select: MESSAGE_SELECT,
    });
    return rows.map(toMessage);
  }

  async addMessage(
    liveId: string,
    userId: string,
    content: string,
  ): Promise<LiveChatMessage> {
    const row = await this.prisma.liveMessage.create({
      data: { liveId, userId, kind: 'CHAT', content },
      select: MESSAGE_SELECT,
    });
    return toMessage(row);
  }

  findAuthor(userId: string): Promise<LiveAuthor | null> {
    return this.prisma.user.findUnique({
      where: { id: userId },
      select: { id: true, displayName: true, avatarUrl: true },
    });
  }
}
