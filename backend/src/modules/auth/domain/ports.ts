import type { AuthenticatedUser, User, UserCredentials } from './user.js';

// Ports du module auth : les cas d'usage n'en connaissent que ces interfaces.

export const USER_REPOSITORY = Symbol('USER_REPOSITORY');
export const PASSWORD_HASHER = Symbol('PASSWORD_HASHER');
export const TOKEN_SERVICE = Symbol('TOKEN_SERVICE');
export const REFRESH_TOKEN_STORE = Symbol('REFRESH_TOKEN_STORE');

export interface NewUser {
  email: string;
  passwordHash: string;
  displayName: string;
}

export interface UserRepository {
  findById(id: string): Promise<User | null>;
  findCredentialsByEmail(email: string): Promise<UserCredentials | null>;
  // Lève EmailAlreadyUsedError si l'email existe déjà.
  create(user: NewUser): Promise<User>;
}

export interface PasswordHasher {
  hash(password: string): Promise<string>;
  verify(hash: string, password: string): Promise<boolean>;
}

export interface RefreshTokenClaims {
  userId: string;
  tokenId: string;
}

export interface TokenService {
  signAccessToken(user: AuthenticatedUser): Promise<string>;
  signRefreshToken(claims: RefreshTokenClaims): Promise<string>;
  // Renvoient null si le token est invalide, expiré ou d'un autre type.
  verifyAccessToken(token: string): Promise<AuthenticatedUser | null>;
  verifyRefreshToken(token: string): Promise<RefreshTokenClaims | null>;
}

// Refresh tokens encore utilisables. Chacun ne sert qu'une fois : il est consommé à l'échange.
export interface RefreshTokenStore {
  save(tokenId: string, userId: string, ttlSeconds: number): Promise<void>;
  // Renvoie l'utilisateur du token et le retire, ou null s'il n'existe pas ou plus.
  consume(tokenId: string): Promise<string | null>;
}
