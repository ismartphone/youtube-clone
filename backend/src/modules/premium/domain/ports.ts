import type { ActiveSubscription } from './premium.js';

// Ports du module premium : les cas d'usage n'en connaissent que ces interfaces.

export const PREMIUM_REPOSITORY = Symbol('PREMIUM_REPOSITORY');
export const CHECKOUT_STORE = Symbol('CHECKOUT_STORE');

export interface PremiumRepository {
  // Abonnement actif à la date [now] (expiresAt dans le futur), ou null.
  findActive(userId: string, now: Date): Promise<ActiveSubscription | null>;
  // purchaseToken est unique : un même jeton ne crée jamais deux abonnements.
  create(
    userId: string,
    purchaseToken: string,
    subscription: ActiveSubscription,
  ): Promise<ActiveSubscription>;
  // Termine à [now] les abonnements actifs. Renvoie false s'il n'y en avait aucun.
  endActive(userId: string, now: Date): Promise<boolean>;
}

// Achats en attente (ADR-012) : notre serveur émet le jeton, puis le vérifie à la confirmation.
export interface CheckoutStore {
  // Émet un jeton aléatoire, rattaché à l'utilisateur, valable [ttlSeconds].
  issue(userId: string, ttlSeconds: number): Promise<string>;
  // Consomme le jeton (usage unique) et renvoie l'utilisateur à qui il a été émis,
  // ou null s'il est inconnu, expiré ou déjà utilisé.
  consume(purchaseToken: string): Promise<string | null>;
}
