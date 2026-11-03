import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type {
  ChannelSearchResult,
  SearchRepository,
} from '../domain/search.repository.js';
import type { Video } from '../domain/video.js';
import type { Page, Pagination } from '../domain/video.repository.js';
import { VIDEO_SELECT } from './prisma-video.repository.js';

// Motif ILIKE « contient le terme ». Les jokers de LIKE saisis par l'utilisateur
// (%, _ et \) sont échappés : ils sont cherchés tels quels.
function containsPattern(term: string): string {
  return `%${term.replace(/[\\%_]/g, '\\$&')}%`;
}

// Prisma ne sait pas appeler unaccent() : les identifiants correspondants sont trouvés
// en SQL paramétré, puis les lignes complètes relues avec les sélections habituelles.
export class PrismaSearchRepository implements SearchRepository {
  constructor(private readonly prisma: PrismaService) {}

  async searchVideos(
    term: string,
    { page, pageSize }: Pagination,
  ): Promise<Page<Video>> {
    const pattern = containsPattern(term);
    const [rows, [{ count }]] = await Promise.all([
      this.prisma.$queryRaw<{ id: string }[]>`
        SELECT id FROM videos
        WHERE unaccent(title) ILIKE unaccent(${pattern}) AND visibility = 'PUBLIC'
        ORDER BY "publishedAt" DESC, id ASC
        LIMIT ${pageSize} OFFSET ${(page - 1) * pageSize}`,
      this.prisma.$queryRaw<{ count: bigint }[]>`
        SELECT count(*) FROM videos
        WHERE unaccent(title) ILIKE unaccent(${pattern}) AND visibility = 'PUBLIC'`,
    ]);
    const ids = rows.map((row) => row.id);
    const videos = await this.prisma.video.findMany({
      where: { id: { in: ids } },
      select: VIDEO_SELECT,
    });
    const byId = new Map(videos.map((video) => [video.id, video]));
    return {
      items: ids.flatMap((id) => byId.get(id) ?? []),
      page,
      pageSize,
      total: Number(count),
    };
  }

  async searchChannels(
    term: string,
    { page, pageSize }: Pagination,
  ): Promise<Page<ChannelSearchResult>> {
    const pattern = containsPattern(term);
    const [rows, [{ count }]] = await Promise.all([
      this.prisma.$queryRaw<{ id: string }[]>`
        SELECT id FROM channels
        WHERE unaccent(name) ILIKE unaccent(${pattern})
        ORDER BY lower(name) ASC, id ASC
        LIMIT ${pageSize} OFFSET ${(page - 1) * pageSize}`,
      this.prisma.$queryRaw<{ count: bigint }[]>`
        SELECT count(*) FROM channels
        WHERE unaccent(name) ILIKE unaccent(${pattern})`,
    ]);
    const ids = rows.map((row) => row.id);
    // Sélection explicite : rien du compte propriétaire ne sort par ce dépôt.
    const channels = await this.prisma.channel.findMany({
      where: { id: { in: ids } },
      select: {
        id: true,
        name: true,
        handle: true,
        avatarUrl: true,
        _count: { select: { followers: true } },
      },
    });
    const byId = new Map(
      channels.map(({ _count, ...channel }) => [
        channel.id,
        { ...channel, followerCount: _count.followers },
      ]),
    );
    return {
      items: ids.flatMap((id) => byId.get(id) ?? []),
      page,
      pageSize,
      total: Number(count),
    };
  }
}
