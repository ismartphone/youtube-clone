import type { JwtService } from '@nestjs/jwt';
import {
  ACCESS_TOKEN_TTL_SECONDS,
  REFRESH_TOKEN_TTL_SECONDS,
} from '../application/token-pair.js';
import type { RefreshTokenClaims, TokenService } from '../domain/ports.js';
import { ROLES, type AuthenticatedUser, type Role } from '../domain/user.js';

export interface JwtSecrets {
  accessSecret: string;
  refreshSecret: string;
}

// Deux secrets distincts, et un champ "type" dans chaque token :
// un refresh token ne peut jamais servir d'access token, ni l'inverse.
interface AccessPayload {
  sub: string;
  role: Role;
  type: 'access';
}

interface RefreshPayload {
  sub: string;
  jti: string;
  type: 'refresh';
}

export class JwtTokenService implements TokenService {
  constructor(
    private readonly jwt: JwtService,
    private readonly secrets: JwtSecrets,
  ) {}

  signAccessToken(user: AuthenticatedUser): Promise<string> {
    const payload: AccessPayload = {
      sub: user.id,
      role: user.role,
      type: 'access',
    };
    return this.jwt.signAsync(payload, {
      secret: this.secrets.accessSecret,
      expiresIn: ACCESS_TOKEN_TTL_SECONDS,
    });
  }

  signRefreshToken({ userId, tokenId }: RefreshTokenClaims): Promise<string> {
    const payload: RefreshPayload = {
      sub: userId,
      jti: tokenId,
      type: 'refresh',
    };
    return this.jwt.signAsync(payload, {
      secret: this.secrets.refreshSecret,
      expiresIn: REFRESH_TOKEN_TTL_SECONDS,
    });
  }

  async verifyAccessToken(token: string): Promise<AuthenticatedUser | null> {
    const payload = await this.verify<AccessPayload>(
      token,
      this.secrets.accessSecret,
    );
    if (payload?.type !== 'access' || !ROLES.includes(payload.role)) {
      return null;
    }
    return { id: payload.sub, role: payload.role };
  }

  async verifyRefreshToken(token: string): Promise<RefreshTokenClaims | null> {
    const payload = await this.verify<RefreshPayload>(
      token,
      this.secrets.refreshSecret,
    );
    if (payload?.type !== 'refresh' || !payload.jti) {
      return null;
    }
    return { userId: payload.sub, tokenId: payload.jti };
  }

  // Signature fausse, token expiré ou malformé : null plutôt qu'une exception.
  private async verify<T extends object>(
    token: string,
    secret: string,
  ): Promise<T | null> {
    try {
      return await this.jwt.verifyAsync<T>(token, { secret });
    } catch {
      return null;
    }
  }
}
