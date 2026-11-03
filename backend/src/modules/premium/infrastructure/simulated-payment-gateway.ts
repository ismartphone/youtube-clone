import { randomBytes } from 'node:crypto';
import type { Redis } from 'ioredis';
import { CHECKOUT_TTL_SECONDS, type PaymentStart } from '../domain/premium.js';
import type { PaymentGateway } from '../domain/ports.js';

// Achats en attente dans Redis : la clé expire d'elle-même, et GETDEL la lit et l'efface
// en une seule opération, ce qui garantit l'usage unique du jeton.
const KEY_PREFIX = 'premium:checkout:';

// Achat simulé par notre propre backend (ADR-012), gardé en repli quand STRIPE_SECRET_KEY est
// absente (ADR-014) : la démonstration reste possible sans réseau. Notre serveur émet le jeton,
// l'application affiche une feuille de paiement simulée, puis renvoie le jeton pour confirmer.
export class SimulatedPaymentGateway implements PaymentGateway {
  readonly mode = 'simulated';
  readonly publishableKey = null;

  constructor(private readonly redis: Redis) {}

  async start(userId: string): Promise<PaymentStart> {
    const reference = randomBytes(24).toString('base64url');
    await this.redis.set(
      KEY_PREFIX + reference,
      userId,
      'EX',
      CHECKOUT_TTL_SECONDS,
    );
    return {
      mode: this.mode,
      reference,
      clientSecret: null,
      expiresAt: new Date(Date.now() + CHECKOUT_TTL_SECONDS * 1000),
    };
  }

  // Le jeton est consommé dans tous les cas : il ne peut jamais servir deux fois.
  async isPaidBy(userId: string, reference: string): Promise<boolean> {
    return (await this.redis.getdel(KEY_PREFIX + reference)) === userId;
  }
}
