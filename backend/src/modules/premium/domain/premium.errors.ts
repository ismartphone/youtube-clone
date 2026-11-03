// Erreurs métier de l'offre Premium, traduites en codes HTTP par la couche interfaces.

export class AlreadyPremiumError extends Error {
  constructor() {
    super('Vous êtes déjà abonné à Premium');
  }
}

// Paiement inconnu, non réglé, expiré ou fait par un autre utilisateur : le détail n'est pas donné.
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

// Paiement déjà utilisé pour accorder un abonnement : une référence ne sert qu'une fois.
export class PaymentAlreadyUsedError extends Error {
  constructor() {
    super('Ce paiement a déjà été utilisé');
  }
}

// Le fournisseur de paiement ne répond pas : rien n'est accordé, l'utilisateur peut réessayer.
export class PaymentUnavailableError extends Error {
  constructor() {
    super(
      'Le paiement est momentanément indisponible : réessayez dans un instant',
    );
  }
}
