import { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { PaymentAlreadyUsedError } from '../domain/premium.errors.js';
import type { ActiveSubscription } from '../domain/premium.js';
import type { PremiumRepository } from '../domain/ports.js';

const SUBSCRIPTION_SELECT = { startedAt: true, expiresAt: true } as const;

// Code Prisma d'une violation de contrainte d'unicité.
const UNIQUE_CONSTRAINT_VIOLATION = 'P2002';

export class PrismaPremiumRepository implements PremiumRepository {
  constructor(private readonly prisma: PrismaService) {}

  findActive(userId: string, now: Date): Promise<ActiveSubscription | null> {
    return this.prisma.premiumSubscription.findFirst({
      where: { userId, expiresAt: { gt: now } },
      orderBy: { expiresAt: 'desc' },
      select: SUBSCRIPTION_SELECT,
    });
  }

  // La contrainte d'unicité sur purchaseToken tranche aussi deux confirmations simultanées
  // du même paiement : la seconde échoue.
  async create(
    userId: string,
    purchaseToken: string,
    subscription: ActiveSubscription,
  ): Promise<ActiveSubscription> {
    try {
      return await this.prisma.premiumSubscription.create({
        data: { userId, purchaseToken, ...subscription },
        select: SUBSCRIPTION_SELECT,
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === UNIQUE_CONSTRAINT_VIOLATION
      ) {
        throw new PaymentAlreadyUsedError();
      }
      throw error;
    }
  }

  async endActive(userId: string, now: Date): Promise<boolean> {
    const { count } = await this.prisma.premiumSubscription.updateMany({
      where: { userId, expiresAt: { gt: now } },
      data: { expiresAt: now },
    });
    return count > 0;
  }
}
