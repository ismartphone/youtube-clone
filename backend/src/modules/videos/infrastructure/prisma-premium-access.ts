import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { PremiumAccess } from '../domain/premium-access.js';

// Lecture seule de la table du module premium : acceptable dans un monolithe à base unique (ADR-003).
export class PrismaPremiumAccess implements PremiumAccess {
  constructor(private readonly prisma: PrismaService) {}

  async isPremium(userId: string): Promise<boolean> {
    const subscription = await this.prisma.premiumSubscription.findFirst({
      where: { userId, expiresAt: { gt: new Date() } },
      select: { id: true },
    });
    return subscription !== null;
  }
}
