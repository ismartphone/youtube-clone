import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import type { AuthenticatedUser } from '../../domain/user.js';
import type { AuthenticatedRequest } from './jwt-auth.guard.js';

// Utilisateur de la requête, renseigné par JwtAuthGuard : à utiliser sur une route protégée.
export const CurrentUser = createParamDecorator(
  (_data: unknown, context: ExecutionContext): AuthenticatedUser =>
    context.switchToHttp().getRequest<AuthenticatedRequest>().user,
);

// Utilisateur de la requête derrière OptionalJwtAuthGuard : undefined pour un visiteur.
export const OptionalCurrentUser = createParamDecorator(
  (_data: unknown, context: ExecutionContext): AuthenticatedUser | undefined =>
    context.switchToHttp().getRequest<Partial<AuthenticatedRequest>>().user,
);
