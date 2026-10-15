import type { CatalogStatsReader } from '../domain/catalog.js';
import type { ViewCounter } from '../domain/view-counter.js';

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
    private readonly viewCounter: ViewCounter,
  ) {}

  async execute(): Promise<CatalogStats> {
    const [counts, pendingViews] = await Promise.all([
      this.stats.counts(),
      this.viewCounter.pendingTotal(),
    ]);
    return {
      users: counts.users,
      channels: counts.channels,
      videos: counts.videos,
      totalViews: counts.storedViews + pendingViews,
    };
  }
}
