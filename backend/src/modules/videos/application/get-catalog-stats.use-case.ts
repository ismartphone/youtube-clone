import type { CatalogStatsReader } from '../domain/catalog.js';
import type { VideoCounters } from '../domain/counters.js';

export interface CatalogStats {
  users: number;
  channels: number;
  videos: number;
  totalViews: number;
}

// Chiffres du tableau de bord. Les vues comprennent celles encore en attente dans le compteur.
export class GetCatalogStatsUseCase {
  constructor(
    private readonly stats: CatalogStatsReader,
    private readonly counters: VideoCounters,
  ) {}

  async execute(): Promise<CatalogStats> {
    const [counts, pendingViews] = await Promise.all([
      this.stats.counts(),
      this.counters.views.pendingTotal(),
    ]);
    return {
      users: counts.users,
      channels: counts.channels,
      videos: counts.videos,
      totalViews: counts.storedViews + pendingViews,
    };
  }
}
