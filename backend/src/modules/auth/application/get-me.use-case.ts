import type { UserRepository } from '../domain/ports.js';
import type { User } from '../domain/user.js';

export class GetMeUseCase {
  constructor(private readonly users: UserRepository) {}

  execute(userId: string): Promise<User | null> {
    return this.users.findById(userId);
  }
}
