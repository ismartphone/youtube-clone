// Erreurs métier des chaînes, traduites en codes HTTP par la couche interfaces.

export class ChannelAlreadyOwnedError extends Error {
  constructor() {
    super('Vous avez déjà une chaîne');
  }
}

export class HandleAlreadyUsedError extends Error {
  constructor() {
    super('Ce handle est déjà utilisé');
  }
}

// Image refusée : ni JPEG ni PNG, ou illisible.
export class InvalidChannelImageError extends Error {
  constructor() {
    super('Choisissez une image JPEG ou PNG.');
  }
}

export class ChannelImageUnavailableError extends Error {
  constructor() {
    super(
      "L'envoi d'images est momentanément indisponible. Réessayez plus tard.",
    );
  }
}

export class ChannelNotFoundError extends Error {
  constructor() {
    super('Chaîne introuvable');
  }
}

// Comme sur YouTube, on ne peut pas s'abonner à sa propre chaîne.
export class CannotFollowOwnChannelError extends Error {
  constructor() {
    super("Impossible de s'abonner à sa propre chaîne");
  }
}

// La cloche n'existe que sur un abonnement (ADR-020).
export class NotFollowingError extends Error {
  constructor() {
    super("Vous n'êtes pas abonné à cette chaîne");
  }
}
