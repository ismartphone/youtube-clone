import type {
  ActiveSubscription,
  PaymentMode,
  PaymentStart,
} from './premium.js';

// Ports du module premium : les cas d'usage n'en connaissent que ces interfaces.

export const PREMIUM_REPOSITORY = Symbol('PREMIUM_REPOSITORY');
export const PAYMENT_GATEWAY = Symbol('PAYMENT_GATEWAY');

export interface PremiumRepository {
  // Abonnement actif à la date [now] (expiresAt dans le futur), ou null.
  findActive(userId: string, now: Date): Promise<ActiveSubscription | null>;
  // purchaseToken est unique : une même référence de paiement ne crée jamais deux abonnements.
  // Lève PaymentAlreadyUsedError si elle a déjà servi.
  create(
    userId: string,
    purchaseToken: string,
    subscription: ActiveSubscription,
  ): Promise<ActiveSubscription>;
  // Termine à [now] les abonnements actifs. Renvoie false s'il n'y en avait aucun.
  endActive(userId: string, now: Date): Promise<boolean>;
}

// Fournisseur de paiement (ADR-014) : Stripe en mode test, ou l'achat simulé de l'ADR-012
// en repli quand la clé Stripe est absente. Le montant est toujours fixé par le serveur.
export interface PaymentGateway {
  readonly mode: PaymentMode;
  // Clé publiable Stripe, transmise à l'application ; null en mode simulé.
  readonly publishableKey: string | null;
  // Démarre le paiement de l'offre pour [userId]. Lève PaymentUnavailableError si le
  // fournisseur ne répond pas.
  start(userId: string): Promise<PaymentStart>;
  // Vérifie que le paiement [reference] est réglé, au bon montant, pour [userId].
  // Lève PaymentUnavailableError si le fournisseur ne répond pas : le paiement reste alors
  // vérifiable plus tard.
  isPaidBy(userId: string, reference: string): Promise<boolean>;
}
