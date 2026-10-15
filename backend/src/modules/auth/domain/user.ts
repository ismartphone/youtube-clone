// Modèles du domaine d'authentification, indépendants de Prisma et de NestJS.

export const ROLES = ['VISITOR', 'USER', 'CREATOR', 'ADMIN'] as const;
export type Role = (typeof ROLES)[number];

// Profil d'un utilisateur. Le hash du mot de passe n'en fait jamais partie.
export interface User {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: Role;
  createdAt: Date;
}

// Utilisateur avec son hash, utilisé uniquement pour vérifier un mot de passe à la connexion.
export interface UserCredentials {
  user: User;
  passwordHash: string;
}

// Identité portée par un access token valide.
export interface AuthenticatedUser {
  id: string;
  role: Role;
}

// Les emails sont comparés sans tenir compte de la casse ni des espaces autour.
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}
