import type { Redis } from 'ioredis';
import type { RefreshTokenStore } from '../domain/ports.js';

const KEY_PREFIX = 'auth:refresh:';

// Un refresh token valide = une clé Redis qui expire avec lui.
export class RedisRefreshTokenStore implements RefreshTokenStore {
  constructor(private readonly redis: Redis) {}

  async save(
    tokenId: string,
    userId: string,
    ttlSeconds: number,
  ): Promise<void> {
    await this.redis.set(KEY_PREFIX + tokenId, userId, 'EX', ttlSeconds);
  }

  // GETDEL est atomique : deux échanges simultanés du même token ne peuvent pas réussir tous les deux.
  consume(tokenId: string): Promise<string | null> {
    return this.redis.getdel(KEY_PREFIX + tokenId);
  }
}
