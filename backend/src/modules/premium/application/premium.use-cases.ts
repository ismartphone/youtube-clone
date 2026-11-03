import {
  CHECKOUT_TTL_SECONDS,
  subscriptionEnd,
  type ActiveSubscription,
  type Checkout,
} from '../domain/premium.js';
import {
  AlreadyPremiumError,
  InvalidPurchaseTokenError,
  NotPremiumError,
} from '../domain/premium.errors.js';
import type { CheckoutStore, PremiumRepository } from '../domain/ports.js';

// Parcours d'achat simulé (ADR-012) : checkout, feuille de paiement simulée dans l'application,
// puis confirmation avec le jeton. Le serveur reste seul juge de l'activation (ADR-007).

export class GetPremiumStatusUseCase {
  constructor(private readonly subscriptions: PremiumRepository) {}

  execute(userId: string): Promise<ActiveSubscription | null> {
    return this.subscriptions.findActive(userId, new Date());
  }
}

export class StartCheckoutUseCase {
  constructor(
    private readonly subscriptions: PremiumRepository,
    private readonly checkouts: CheckoutStore,
  ) {}

  // Lève AlreadyPremiumError si l'utilisateur a déjà un abonnement en cours.
  async execute(userId: string): Promise<Checkout> {
    if (await this.subscriptions.findActive(userId, new Date())) {
      throw new AlreadyPremiumError();
    }
    const purchaseToken = await this.checkouts.issue(userId, CHECKOUT_TTL_SECONDS);
    return {
      purchaseToken,
      expiresAt: new Date(Date.now() + CHECKOUT_TTL_SECONDS * 1000),
    };
  }
}

export class ConfirmPurchaseUseCase {
  constructor(
    private readonly subscriptions: PremiumRepository,
    private readonly checkouts: CheckoutStore,
  ) {}

  // Lève InvalidPurchaseTokenError si le jeton n'est pas valable pour cet utilisateur,
  // AlreadyPremiumError s'il a déjà un abonnement en cours.
  async execute(userId: string, purchaseToken: string): Promise<ActiveSubscription> {
    // Le jeton est consommé dans tous les cas : il ne peut jamais servir deux fois.
    const owner = await this.checkouts.consume(purchaseToken);
    if (owner !== userId) {
      throw new InvalidPurchaseTokenError();
    }
    const now = new Date();
    if (await this.subscriptions.findActive(userId, now)) {
      throw new AlreadyPremiumError();
    }
    return this.subscriptions.create(userId, purchaseToken, {
      startedAt: now,
      expiresAt: subscriptionEnd(now),
    });
  }
}

export class CancelPremiumUseCase {
  constructor(private readonly subscriptions: PremiumRepository) {}

  // Fin immédiate : pas de renouvellement automatique à arrêter, le paiement étant simulé.
  // Lève NotPremiumError s'il n'y avait aucun abonnement en cours.
  async execute(userId: string): Promise<void> {
    if (!(await this.subscriptions.endActive(userId, new Date()))) {
      throw new NotPremiumError();
    }
  }
}
