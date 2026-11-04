// Statut Premium du spectateur, lu pour décider de l'accès aux vidéos exclusives (ADR-007).
// L'abonnement lui-même est géré par le module premium.

export const PREMIUM_ACCESS = Symbol('PREMIUM_ACCESS');

export interface PremiumAccess {
  // Vrai si l'utilisateur a un abonnement Premium en cours.
  isPremium(userId: string): Promise<boolean>;
  // Fin de l'abonnement en cours, ou null s'il n'en a pas (téléchargements, ADR-019).
  activeUntil(userId: string): Promise<Date | null>;
}
