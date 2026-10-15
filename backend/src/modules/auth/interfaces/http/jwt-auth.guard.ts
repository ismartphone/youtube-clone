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

// Lit l'access token de l'en-tête Authorization: Bearer <token>.
// Renvoie null sans en-tête, et lève une 401 si le token est présent mais invalide ou expiré.
async function authenticate(
  request: Request,
  tokens: TokenService,
): Promise<AuthenticatedUser | null> {
  const header = request.headers.authorization;
  if (!header) {
    return null;
  }
  const [scheme, token] = header.split(' ');
  if (scheme !== 'Bearer' || !token) {
    throw new UnauthorizedException('Authentification requise');
  }
  const user = await tokens.verifyAccessToken(token);
  if (!user) {
    throw new UnauthorizedException('Access token invalide ou expiré');
  }
  return user;
}

// Exige un access token valide.
// Réutilisable par tout module qui importe AuthModule : @UseGuards(JwtAuthGuard).
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(@Inject(TOKEN_SERVICE) private readonly tokens: TokenService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const user = await authenticate(request, this.tokens);
    if (!user) {
      throw new UnauthorizedException('Authentification requise');
    }
    request.user = user;
    return true;
  }
}

// Route ouverte aux visiteurs, enrichie quand l'utilisateur est connecté.
// Sans en-tête, la requête passe en visiteur. Un token expiré renvoie quand même 401 :
// le client le renouvelle au lieu d'afficher à tort une réponse de visiteur.
@Injectable()
export class OptionalJwtAuthGuard implements CanActivate {
  constructor(@Inject(TOKEN_SERVICE) private readonly tokens: TokenService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context
      .switchToHttp()
      .getRequest<Request & { user?: AuthenticatedUser }>();
    request.user = (await authenticate(request, this.tokens)) ?? undefined;
    return true;
  }
}
