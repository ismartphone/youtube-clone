// Erreurs métier des posts, traduites en codes HTTP par la couche interfaces.

export class NoChannelError extends Error {
  constructor() {
    super('Créez une chaîne pour publier des posts.');
  }
}

export class ChannelNotFoundError extends Error {
  constructor() {
    super('Chaîne introuvable');
  }
}

export class InvalidImageError extends Error {
  constructor() {
    super("Cette image n'est pas lisible. Choisissez une image JPEG ou PNG.");
  }
}

export class ImageUnavailableError extends Error {
  constructor() {
    super(
      "L'envoi d'images est momentanément indisponible sur le serveur. Publiez sans image ou réessayez plus tard.",
    );
  }
}
