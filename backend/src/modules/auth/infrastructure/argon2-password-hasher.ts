import * as argon2 from 'argon2';
import type { PasswordHasher } from '../domain/ports.js';

// Argon2id avec les paramètres par défaut de la bibliothèque (recommandés par l'OWASP).
export class Argon2PasswordHasher implements PasswordHasher {
  hash(password: string): Promise<string> {
    return argon2.hash(password, { type: argon2.argon2id });
  }

  async verify(hash: string, password: string): Promise<boolean> {
    try {
      return await argon2.verify(hash, password);
    } catch {
      // Hash illisible (format inattendu) : traité comme un mot de passe faux.
      return false;
    }
  }
}
