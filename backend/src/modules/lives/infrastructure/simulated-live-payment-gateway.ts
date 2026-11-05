import { randomBytes } from 'node:crypto';
import type { Redis } from 'ioredis';
import type {
  LivePaymentGateway,
  LivePaymentStart,
  LiveProductCard,
  VerifiedLivePayment,
} from '../domain/shopping.js';

// Achats en attente dans Redis : la clé expire d'elle-même, et GETDEL la lit et l'efface en une
// seule opération, ce qui garantit l'usage unique du jeton (ADR-012, ADR-014).
const KEY_PREFIX = 'live:checkout:';
const CHECKOUT_TTL_SECONDS = 10 * 60;

// Achat en attente : l'acheteur, le live et le produit au prix figé au démarrage.
interface PendingCheckout extends VerifiedLivePayment {
  userId: string;
  liveId: string;
}

// Repli simulé quand STRIPE_SECRET_KEY est absente (ADR-029, D8) : la démonstration reste possible
// sans réseau. Notre serveur émet le jeton, l'application affiche une feuille de paiement
// simulée, puis renvoie le jeton pour confirmer.
export class SimulatedLivePaymentGateway implements LivePaymentGateway {
  constructor(private readonly redis: Redis) {}

  async start(
    buyerId: string,
    liveId: string,
    product: LiveProductCard,
  ): Promise<LivePaymentStart> {
    const reference = randomBytes(24).toString('base64url');
    const pending: PendingCheckout = {
      userId: buyerId,
      liveId,
      productId: product.id,
      productName: product.name,
      amount: product.price,
      currency: product.currency,
    };
    await this.redis.set(
      KEY_PREFIX + reference,
      JSON.stringify(pending),
      'EX',
      CHECKOUT_TTL_SECONDS,
    );
    return {
      mode: 'simulated',
      reference,
      clientSecret: null,
      publishableKey: null,
      expiresAt: new Date(Date.now() + CHECKOUT_TTL_SECONDS * 1000),
    };
  }

  // Le jeton est consommé dans tous les cas : il ne peut jamais servir deux fois.
  async verify(
    buyerId: string,
    liveId: string,
    reference: string,
  ): Promise<VerifiedLivePayment | null> {
    const stored = await this.redis.getdel(KEY_PREFIX + reference);
    if (!stored) {
      return null;
    }
    const pending = JSON.parse(stored) as PendingCheckout;
    if (pending.userId !== buyerId || pending.liveId !== liveId) {
      return null;
    }
    return {
      productId: pending.productId,
      productName: pending.productName,
      amount: pending.amount,
      currency: pending.currency,
    };
  }
}
