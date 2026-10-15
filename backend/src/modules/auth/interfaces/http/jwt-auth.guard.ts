import {
  CanActivate,
  ExecutionContext,
  Inject,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import type { Request } from 'express';
import { TOKEN_SERVICE, type TokenService } from '../../domain/ports.js';
import type { AuthenticatedUser } from '../../domain/user.js';

export type AuthenticatedRequest = Request & { user: AuthenticatedUser };

// Exige un access token valide dans l'en-tête Authorization: Bearer <token>.
// Réutilisable par tout module qui importe AuthModule : @UseGuards(JwtAuthGuard).
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(@Inject(TOKEN_SERVICE) private readonly tokens: TokenService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const [scheme, token] = request.headers.authorization?.split(' ') ?? [];
    if (scheme !== 'Bearer' || !token) {
      throw new UnauthorizedException('Authentification requise');
    }
    const user = await this.tokens.verifyAccessToken(token);
    if (!user) {
      throw new UnauthorizedException('Access token invalide ou expiré');
    }
    request.user = user;
    return true;
  }
}
