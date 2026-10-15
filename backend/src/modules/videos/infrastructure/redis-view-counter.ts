import { Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import type { Redis } from 'ioredis';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { ViewCounter } from '../domain/view-counter.js';

// Les vues s'accumulent dans un hash Redis (videoId -> nombre de vues),
// puis sont reportées en base par lot, pour ne pas écrire à chaque vue (ADR-004).
const PENDING_KEY = 'videos:views:pending';
// Lot en cours de report. S'il reste après un échec, il est repris au report suivant.
const FLUSHING_KEY = 'videos:views:flushing';
const FLUSH_INTERVAL_MS = 30_000;

export class RedisViewCounter
  implements ViewCounter, OnModuleInit, OnModuleDestroy
{
  private readonly logger = new Logger(RedisViewCounter.name);
  private timer?: NodeJS.Timeout;
  private flushing?: Promise<void>;

  constructor(
    private readonly redis: Redis,
    private readonly prisma: PrismaService,
  ) {}

  async increment(videoId: string): Promise<void> {
    await this.redis.hincrby(PENDING_KEY, videoId, 1);
  }

  async pendingCounts(videoIds: string[]): Promise<Map<string, number>> {
    const counts = new Map<string, number>();
    if (videoIds.length === 0) {
      return counts;
    }
    const [pending, flushing] = await Promise.all([
      this.redis.hmget(PENDING_KEY, ...videoIds),
      this.redis.hmget(FLUSHING_KEY, ...videoIds),
    ]);
    videoIds.forEach((id, i) => {
      const total = Number(pending[i] ?? 0) + Number(flushing[i] ?? 0);
      if (total > 0) {
        counts.set(id, total);
      }
    });
    return counts;
  }

  onModuleInit() {
    this.timer = setInterval(() => void this.flush(), FLUSH_INTERVAL_MS);
  }

  // À l'arrêt, les vues encore en attente sont reportées avant la fermeture des connexions.
  async onModuleDestroy() {
    clearInterval(this.timer);
    await this.flush();
  }

  flush(): Promise<void> {
    // Un seul report à la fois, même si le précédent dépasse l'intervalle.
    this.flushing ??= this.flushOnce().finally(
      () => (this.flushing = undefined),
    );
    return this.flushing;
  }

  private async flushOnce(): Promise<void> {
    try {
      if (!(await this.redis.exists(FLUSHING_KEY))) {
        // RENAME est atomique : les vues qui arrivent pendant le report repartent dans un nouveau hash.
        const renamed = await this.redis
          .renamenx(PENDING_KEY, FLUSHING_KEY)
          .catch(() => 0);
        if (!renamed) {
          return;
        }
      }
      const batch = await this.redis.hgetall(FLUSHING_KEY);
      const updates = Object.entries(batch).map(([id, count]) =>
        this.prisma.video.updateMany({
          where: { id },
          data: { viewCount: { increment: Number(count) } },
        }),
      );
      await this.prisma.$transaction(updates);
      await this.redis.del(FLUSHING_KEY);
    } catch (error) {
      this.logger.error(
        'Report des vues en base impossible, nouvel essai au prochain cycle',
        error,
      );
    }
  }
}
