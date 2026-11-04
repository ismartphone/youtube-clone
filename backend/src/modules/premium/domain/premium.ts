// Offre Premium (ADR-007), payée par Stripe en mode test ou par l'achat simulé de repli (ADR-014).
// PREMIUM_SUBSCRIPTION est l'abonnement payant : à ne jamais confondre avec FOLLOW.

// Durée accordée par un paiement : un mois, sans renouvellement automatique (ADR-014).
export const SUBSCRIPTION_MONTHS = 1;

// Prix de l'offre, fixé par le serveur et jamais reçu du client : 4,99 € en centimes.
export const PREMIUM_PRICE = { amount: 499, currency: 'eur' } as const;

// Durée de validité d'un jeton d'achat simulé entre « commencer l'achat » et « confirmer ».
export const CHECKOUT_TTL_SECONDS = 10 * 60;

// stripe : PaymentSheet Stripe ; simulated : feuille de paiement simulée (repli, ADR-012).
export type PaymentMode = 'stripe' | 'simulated';

// Abonnement en cours d'un utilisateur ; null s'il n'est pas Premium.
export interface ActiveSubscription {
  startedAt: Date;
  expiresAt: Date;
}

// Paiement démarré. [reference] est renvoyée pour confirmer : jeton simulé ou identifiant du
// PaymentIntent. [clientSecret] sert à la PaymentSheet (null en mode simulé). [expiresAt] :
// fin de validité du jeton simulé (null pour Stripe).
export interface PaymentStart {
  mode: PaymentMode;
  reference: string;
  clientSecret: string | null;
  expiresAt: Date | null;
}

// Montant réellement payé, en centimes, et sa devise ISO en minuscules (« eur »).
export interface Payment {
  amount: number;
  currency: string;
}

// Abonnement tel que le voit l'administration (données personnelles et financières).
export interface SubscriptionRecord {
  id: string;
  user: { displayName: string; email: string };
  startedAt: Date;
  expiresAt: Date;
  amount: number;
  currency: string;
  // Identifiant du PaymentIntent Stripe (pi_…), ou jeton simulé de repli.
  paymentReference: string;
}

// Recettes d'un mois (« 2026-11 ») dans une devise.
export interface MonthlyRevenue {
  month: string;
  currency: string;
  amount: number;
}

export interface RevenueStats {
  // Total encaissé, par devise.
  totals: Payment[];
  activeCount: number;
  expiredCount: number;
  monthly: MonthlyRevenue[];
}

// Fin d'un abonnement acheté maintenant : même jour du mois suivant, ou dernier jour de ce mois
// quand ce jour n'existe pas (31 janvier : 28 ou 29 février, jamais 3 mars).
export function subscriptionEnd(start: Date): Date {
  const end = new Date(start);
  end.setDate(1);
  end.setMonth(end.getMonth() + SUBSCRIPTION_MONTHS);
  const lastDay = new Date(end.getFullYear(), end.getMonth() + 1, 0).getDate();
  end.setDate(Math.min(start.getDate(), lastDay));
  return end;
}
