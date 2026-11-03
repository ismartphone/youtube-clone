import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { ActiveSubscription } from '../domain/premium.js';
import type { PremiumRepository } from '../domain/ports.js';

const SUBSCRIPTION_SELECT = { startedAt: true, expiresAt: true } as const;

export class PrismaPremiumRepository implements PremiumRepository {
  constructor(private readonly prisma: PrismaService) {}

  findActive(userId: string, now: Date): Promise<ActiveSubscription | null> {
    return this.prisma.premiumSubscription.findFirst({
      where: { userId, expiresAt: { gt: now } },
      orderBy: { expiresAt: 'desc' },
      select: SUBSCRIPTION_SELECT,
    });
  }

  create(
    userId: string,
    purchaseToken: string,
    subscription: ActiveSubscription,
  ): Promise<ActiveSubscription> {
    return this.prisma.premiumSubscription.create({
      data: { userId, purchaseToken, ...subscription },
      select: SUBSCRIPTION_SELECT,
    });
  }

  async endActive(userId: string, now: Date): Promise<boolean> {
    const { count } = await this.prisma.premiumSubscription.updateMany({
      where: { userId, expiresAt: { gt: now } },
      data: { expiresAt: now },
    });
    return count > 0;
  }
}
