import { randomUUID } from 'node:crypto';
import type { RefreshTokenStore, TokenService } from '../domain/ports.js';
import type { AuthenticatedUser } from '../domain/user.js';

export const ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
export const REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

// Émet un couple de tokens et enregistre le refresh token pour qu'il puisse servir une fois.
export async function issueTokenPair(
  user: AuthenticatedUser,
  tokens: TokenService,
  refreshStore: RefreshTokenStore,
): Promise<TokenPair> {
  const tokenId = randomUUID();
  const [accessToken, refreshToken] = await Promise.all([
    tokens.signAccessToken(user),
    tokens.signRefreshToken({ userId: user.id, tokenId }),
  ]);
  await refreshStore.save(tokenId, user.id, REFRESH_TOKEN_TTL_SECONDS);
  return { accessToken, refreshToken, expiresIn: ACCESS_TOKEN_TTL_SECONDS };
}
