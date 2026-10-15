// Utilisateur renvoyé par GET /auth/me.

export type Role = "VISITOR" | "USER" | "CREATOR" | "ADMIN";

export interface User {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: Role;
}

// Seuls les administrateurs entrent dans le back-office. L'API le vérifie aussi sur chaque route.
export function isAdmin(user: User): boolean {
  return user.role === "ADMIN";
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}
