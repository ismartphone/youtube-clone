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
