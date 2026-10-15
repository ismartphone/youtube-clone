// Erreurs métier de l'authentification, traduites en codes HTTP par la couche interfaces.

export class EmailAlreadyUsedError extends Error {
  constructor() {
    super('Cette adresse email est déjà utilisée');
  }
}

// Message volontairement identique que l'email soit inconnu ou le mot de passe faux,
// pour ne pas révéler quels comptes existent.
export class InvalidCredentialsError extends Error {
  constructor() {
    super('Email ou mot de passe incorrect');
  }
}

export class InvalidRefreshTokenError extends Error {
  constructor() {
    super('Refresh token invalide ou expiré');
  }
}
