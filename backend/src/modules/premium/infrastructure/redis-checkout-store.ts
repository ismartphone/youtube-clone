import { randomBytes } from 'node:crypto';
import type { Redis } from 'ioredis';
import type { CheckoutStore } from '../domain/ports.js';

// Achats en attente dans Redis : la clé expire d'elle-même, et GETDEL la lit et l'efface
// en une seule opération, ce qui garantit l'usage unique du jeton.
const KEY_PREFIX = 'premium:checkout:';

export class RedisCheckoutStore implements CheckoutStore {
  constructor(private readonly redis: Redis) {}

  async issue(userId: string, ttlSeconds: number): Promise<string> {
    const purchaseToken = randomBytes(24).toString('base64url');
    await this.redis.set(KEY_PREFIX + purchaseToken, userId, 'EX', ttlSeconds);
    return purchaseToken;
  }

  consume(purchaseToken: string): Promise<string | null> {
    return this.redis.getdel(KEY_PREFIX + purchaseToken);
  }
}
