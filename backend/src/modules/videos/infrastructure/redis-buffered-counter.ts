import { Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import type { Redis } from 'ioredis';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { BufferedCounter } from '../domain/counters.js';

// Les variations s'accumulent dans un hash Redis (videoId -> variation),
// puis sont reportées en base par lot, pour ne pas écrire à chaque événement (ADR-004).
const FLUSH_INTERVAL_MS = 30_000;

export interface CounterConfig {
  // Nom court, utilisé dans les clés Redis et les journaux : "views", "likes".
  name: string;
  // Colonne de la table videos qui reçoit les totaux.
  column: 'viewCount' | 'likeCount';
}

export class RedisBufferedCounter
  implements BufferedCounter, OnModuleInit, OnModuleDestroy
{
  private readonly logger: Logger;
  private readonly pendingKey: string;
  // Lot en cours de report. S'il reste après un échec, il est repris au report suivant.
  private readonly flushingKey: string;
  private timer?: NodeJS.Timeout;
  private flushing?: Promise<void>;

  constructor(
    private readonly redis: Redis,
    private readonly prisma: PrismaService,
    private readonly config: CounterConfig,
  ) {
    this.logger = new Logger(`RedisBufferedCounter:${config.name}`);
    this.pendingKey = `videos:${config.name}:pending`;
    this.flushingKey = `videos:${config.name}:flushing`;
  }

  async increment(videoId: string, delta = 1): Promise<void> {
    await this.redis.hincrby(this.pendingKey, videoId, delta);
  }

  async pendingCounts(videoIds: string[]): Promise<Map<string, number>> {
    const counts = new Map<string, number>();
    if (videoIds.length === 0) {
      return counts;
    }
    const [pending, flushing] = await Promise.all([
      this.redis.hmget(this.pendingKey, ...videoIds),
      this.redis.hmget(this.flushingKey, ...videoIds),
    ]);
    videoIds.forEach((id, i) => {
      const total = Number(pending[i] ?? 0) + Number(flushing[i] ?? 0);
      if (total !== 0) {
        counts.set(id, total);
      }
    });
    return counts;
  }

  async pendingTotal(): Promise<number> {
    const [pending, flushing] = await Promise.all([
      this.redis.hvals(this.pendingKey),
      this.redis.hvals(this.flushingKey),
    ]);
    return [...pending, ...flushing].reduce(
      (sum, count) => sum + Number(count),
      0,
    );
  }

  onModuleInit() {
    this.timer = setInterval(() => void this.flush(), FLUSH_INTERVAL_MS);
  }

  // À l'arrêt, les variations encore en attente sont reportées avant la fermeture des connexions.
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
      if (!(await this.redis.exists(this.flushingKey))) {
        // RENAME est atomique : les variations qui arrivent pendant le report repartent dans un nouveau hash.
        const renamed = await this.redis
          .renamenx(this.pendingKey, this.flushingKey)
          .catch(() => 0);
        if (!renamed) {
          return;
        }
      }
      const batch = await this.redis.hgetall(this.flushingKey);
      const updates = Object.entries(batch).map(([id, delta]) =>
        this.prisma.video.updateMany({
          where: { id },
          data: { [this.config.column]: { increment: Number(delta) } },
        }),
      );
      await this.prisma.$transaction(updates);
      await this.redis.del(this.flushingKey);
    } catch (error) {
      this.logger.error(
        'Report en base impossible, nouvel essai au prochain cycle',
        error,
      );
    }
  }
}
