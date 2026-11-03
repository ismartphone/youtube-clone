// Offre Premium (ADR-007) et achat simulé par notre propre backend (ADR-012).
// PREMIUM_SUBSCRIPTION est l'abonnement payant : à ne jamais confondre avec FOLLOW.

// Durée d'un abonnement acheté : un mois.
export const SUBSCRIPTION_MONTHS = 1;

// Durée de validité d'un jeton d'achat entre « commencer l'achat » et « confirmer ».
export const CHECKOUT_TTL_SECONDS = 10 * 60;

// Abonnement en cours d'un utilisateur ; null s'il n'est pas Premium.
export interface ActiveSubscription {
  startedAt: Date;
  expiresAt: Date;
}

// Achat en attente : le jeton à renvoyer pour confirmer, et sa date d'expiration.
export interface Checkout {
  purchaseToken: string;
  expiresAt: Date;
}

// Fin d'un abonnement acheté maintenant : même jour du mois suivant.
export function subscriptionEnd(start: Date): Date {
  const end = new Date(start);
  end.setMonth(end.getMonth() + SUBSCRIPTION_MONTHS);
  return end;
}
