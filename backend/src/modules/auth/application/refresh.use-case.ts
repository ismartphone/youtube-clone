import { InvalidRefreshTokenError } from '../domain/auth.errors.js';
import type {
  RefreshTokenStore,
  TokenService,
  UserRepository,
} from '../domain/ports.js';
import { issueTokenPair, type TokenPair } from './token-pair.js';

// Échange un refresh token contre un nouveau couple. L'ancien est consommé :
// le rejouer une seconde fois est refusé.
export class RefreshUseCase {
  constructor(
    private readonly users: UserRepository,
    private readonly tokens: TokenService,
    private readonly refreshStore: RefreshTokenStore,
  ) {}

  async execute(refreshToken: string): Promise<TokenPair> {
    const claims = await this.tokens.verifyRefreshToken(refreshToken);
    if (!claims) {
      throw new InvalidRefreshTokenError();
    }
    const ownerId = await this.refreshStore.consume(claims.tokenId);
    if (ownerId !== claims.userId) {
      throw new InvalidRefreshTokenError();
    }
    // Le rôle est relu en base : un changement de rôle s'applique au prochain renouvellement.
    const user = await this.users.findById(claims.userId);
    if (!user) {
      throw new InvalidRefreshTokenError();
    }
    return issueTokenPair(
      { id: user.id, role: user.role },
      this.tokens,
      this.refreshStore,
    );
  }
}
