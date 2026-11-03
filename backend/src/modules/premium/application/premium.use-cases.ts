import {
  subscriptionEnd,
  type ActiveSubscription,
  type PaymentMode,
  type PaymentStart,
} from '../domain/premium.js';
import {
  AlreadyPremiumError,
  InvalidPurchaseTokenError,
  NotPremiumError,
} from '../domain/premium.errors.js';
import type { PaymentGateway, PremiumRepository } from '../domain/ports.js';

// Parcours d'achat (ADR-014) : le serveur démarre le paiement, l'application le fait régler
// (PaymentSheet Stripe ou feuille simulée de repli), puis le serveur vérifie le paiement avant
// d'accorder un mois de Premium. Le serveur reste seul juge de l'activation (ADR-007).

export class GetPremiumStatusUseCase {
  constructor(private readonly subscriptions: PremiumRepository) {}

  execute(userId: string): Promise<ActiveSubscription | null> {
    return this.subscriptions.findActive(userId, new Date());
  }
}

export interface PaymentConfig {
  mode: PaymentMode;
  publishableKey: string | null;
}

// Mode de paiement actif, lu par l'application à l'ouverture du paywall.
export class GetPaymentConfigUseCase {
  constructor(private readonly payments: PaymentGateway) {}

  execute(): PaymentConfig {
    return {
      mode: this.payments.mode,
      publishableKey: this.payments.publishableKey,
    };
  }
}

export class StartCheckoutUseCase {
  constructor(
    private readonly subscriptions: PremiumRepository,
    private readonly payments: PaymentGateway,
  ) {}

  // Lève AlreadyPremiumError si l'utilisateur a déjà un abonnement en cours.
  async execute(userId: string): Promise<PaymentStart> {
    if (await this.subscriptions.findActive(userId, new Date())) {
      throw new AlreadyPremiumError();
    }
    return this.payments.start(userId);
  }
}

export class ConfirmPurchaseUseCase {
  constructor(
    private readonly subscriptions: PremiumRepository,
    private readonly payments: PaymentGateway,
  ) {}

  // Lève InvalidPurchaseTokenError si le paiement n'est pas réglé par cet utilisateur,
  // AlreadyPremiumError s'il a déjà un abonnement en cours, PaymentAlreadyUsedError si ce
  // paiement a déjà accordé un abonnement.
  async execute(
    userId: string,
    reference: string,
  ): Promise<ActiveSubscription> {
    if (!(await this.payments.isPaidBy(userId, reference))) {
      throw new InvalidPurchaseTokenError();
    }
    const now = new Date();
    if (await this.subscriptions.findActive(userId, now)) {
      throw new AlreadyPremiumError();
    }
    // La référence est enregistrée dans purchaseToken, unique : elle ne sert qu'une fois.
    return this.subscriptions.create(userId, reference, {
      startedAt: now,
      expiresAt: subscriptionEnd(now),
    });
  }
}

export class CancelPremiumUseCase {
  constructor(private readonly subscriptions: PremiumRepository) {}

  // Fin immédiate : un paiement donne un mois sans renouvellement, il n'y a rien à arrêter
  // chez le fournisseur. Lève NotPremiumError s'il n'y avait aucun abonnement en cours.
  async execute(userId: string): Promise<void> {
    if (!(await this.subscriptions.endActive(userId, new Date()))) {
      throw new NotPremiumError();
    }
  }
}
