import { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { EmailAlreadyUsedError } from '../domain/auth.errors.js';
import type { NewUser, UserRepository } from '../domain/ports.js';
import type { User, UserCredentials } from '../domain/user.js';

// Champs du profil. passwordHash n'est sélectionné que par findCredentialsByEmail.
const USER_SELECT = {
  id: true,
  email: true,
  displayName: true,
  avatarUrl: true,
  role: true,
  createdAt: true,
} satisfies Prisma.UserSelect;

// Code Prisma d'une violation de contrainte d'unicité.
const UNIQUE_CONSTRAINT_VIOLATION = 'P2002';

export class PrismaUserRepository implements UserRepository {
  constructor(private readonly prisma: PrismaService) {}

  findById(id: string): Promise<User | null> {
    return this.prisma.user.findUnique({ where: { id }, select: USER_SELECT });
  }

  async findCredentialsByEmail(email: string): Promise<UserCredentials | null> {
    const row = await this.prisma.user.findUnique({
      where: { email },
      select: { ...USER_SELECT, passwordHash: true },
    });
    if (!row) {
      return null;
    }
    const { passwordHash, ...user } = row;
    return { user, passwordHash };
  }

  async create(newUser: NewUser): Promise<User> {
    try {
      return await this.prisma.user.create({
        data: newUser,
        select: USER_SELECT,
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === UNIQUE_CONSTRAINT_VIOLATION
      ) {
        throw new EmailAlreadyUsedError();
      }
      throw error;
    }
  }
}
