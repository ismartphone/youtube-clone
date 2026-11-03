// Erreurs métier de l'offre Premium, traduites en codes HTTP par la couche interfaces.

export class AlreadyPremiumError extends Error {
  constructor() {
    super('Vous êtes déjà abonné à Premium');
  }
}

// Jeton inconnu, expiré, déjà utilisé ou émis pour un autre utilisateur : le détail n'est pas donné.
export class InvalidPurchaseTokenError extends Error {
  constructor() {
    super("Achat expiré ou invalide : recommencez l'achat");
  }
}

export class NotPremiumError extends Error {
  constructor() {
    super("Vous n'avez pas d'abonnement Premium en cours");
  }
}
