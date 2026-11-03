import type {
  ActiveSubscription,
  Payment,
  PaymentMode,
  PaymentStart,
  RevenueStats,
  SubscriptionRecord,
} from './premium.js';

// Ports du module premium : les cas d'usage n'en connaissent que ces interfaces.

export const PREMIUM_REPOSITORY = Symbol('PREMIUM_REPOSITORY');
export const PAYMENT_GATEWAY = Symbol('PAYMENT_GATEWAY');

export interface PremiumRepository {
  // Abonnement actif à la date [now] (expiresAt dans le futur), ou null.
  findActive(userId: string, now: Date): Promise<ActiveSubscription | null>;
  // purchaseToken est unique : une même référence de paiement ne crée jamais deux abonnements.
  // Lève PaymentAlreadyUsedError si elle a déjà servi.
  // [payment] : ce qui a réellement été payé, enregistré avec l'abonnement.
  create(
    userId: string,
    purchaseToken: string,
    subscription: ActiveSubscription,
    payment: Payment,
  ): Promise<ActiveSubscription>;
  // Termine à [now] les abonnements actifs. Renvoie false s'il n'y en avait aucun.
  endActive(userId: string, now: Date): Promise<boolean>;
  // Administration : tous les abonnements, du plus récent au plus ancien.
  findPage(
    page: number,
    pageSize: number,
  ): Promise<{ items: SubscriptionRecord[]; total: number }>;
  // Administration : total encaissé, abonnements actifs et expirés à [now], recettes par mois.
  revenueStats(now: Date): Promise<RevenueStats>;
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
  // Vérifie que le paiement [reference] est réglé, au bon montant, pour [userId], et renvoie
  // ce qui a réellement été payé ; null sinon. Lève PaymentUnavailableError si le fournisseur
  // ne répond pas : le paiement reste alors vérifiable plus tard.
  verifyPayment(userId: string, reference: string): Promise<Payment | null>;
}
