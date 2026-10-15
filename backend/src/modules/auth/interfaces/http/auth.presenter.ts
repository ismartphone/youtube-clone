import type { TokenPair } from '../../application/token-pair.js';
import type { User } from '../../domain/user.js';

// Forme exacte des réponses : chaque champ exposé est listé ici.

export function presentUser(user: User) {
  return {
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    avatarUrl: user.avatarUrl,
    role: user.role,
    createdAt: user.createdAt,
  };
}

export function presentTokens(tokens: TokenPair) {
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    tokenType: 'Bearer',
    expiresIn: tokens.expiresIn,
  };
}
