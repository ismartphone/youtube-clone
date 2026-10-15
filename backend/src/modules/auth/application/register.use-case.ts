import type { PasswordHasher, UserRepository } from '../domain/ports.js';
import { normalizeEmail, type User } from '../domain/user.js';

export interface RegisterCommand {
  email: string;
  password: string;
  displayName: string;
}

// Crée un compte avec le rôle par défaut (USER). Ne connecte pas l'utilisateur.
export class RegisterUseCase {
  constructor(
    private readonly users: UserRepository,
    private readonly hasher: PasswordHasher,
  ) {}

  async execute(command: RegisterCommand): Promise<User> {
    return this.users.create({
      email: normalizeEmail(command.email),
      passwordHash: await this.hasher.hash(command.password),
      displayName: command.displayName.trim(),
    });
  }
}
