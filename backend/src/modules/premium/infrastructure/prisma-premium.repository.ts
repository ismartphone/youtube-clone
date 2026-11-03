import { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { PaymentAlreadyUsedError } from '../domain/premium.errors.js';
import type {
  ActiveSubscription,
  Payment,
  RevenueStats,
  SubscriptionRecord,
} from '../domain/premium.js';
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
    payment: Payment,
  ): Promise<ActiveSubscription> {
    try {
      return await this.prisma.premiumSubscription.create({
        data: { userId, purchaseToken, ...subscription, ...payment },
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

  // Données personnelles (nom, email) et financières : lues pour l'administration seulement.
  async findPage(
    page: number,
    pageSize: number,
  ): Promise<{ items: SubscriptionRecord[]; total: number }> {
    const [rows, total] = await this.prisma.$transaction([
      this.prisma.premiumSubscription.findMany({
        orderBy: [{ startedAt: 'desc' }, { id: 'asc' }],
        skip: (page - 1) * pageSize,
        take: pageSize,
        select: {
          id: true,
          startedAt: true,
          expiresAt: true,
          amount: true,
          currency: true,
          purchaseToken: true,
          user: { select: { displayName: true, email: true } },
        },
      }),
      this.prisma.premiumSubscription.count(),
    ]);
    return {
      items: rows.map(({ purchaseToken, ...row }) => ({
        ...row,
        paymentReference: purchaseToken,
      })),
      total,
    };
  }

  async revenueStats(now: Date): Promise<RevenueStats> {
    const [totals, activeCount, expiredCount, monthly] = await Promise.all([
      this.prisma.premiumSubscription.groupBy({
        by: ['currency'],
        _sum: { amount: true },
        orderBy: { currency: 'asc' },
      }),
      this.prisma.premiumSubscription.count({
        where: { expiresAt: { gt: now } },
      }),
      this.prisma.premiumSubscription.count({
        where: { expiresAt: { lte: now } },
      }),
      // Douze derniers mois, mois de souscription, en heure de Paris.
      this.prisma.$queryRaw<
        { month: string; currency: string; amount: bigint }[]
      >`
        SELECT to_char(date_trunc('month', "startedAt" AT TIME ZONE 'Europe/Paris'), 'YYYY-MM') AS month,
               currency,
               sum(amount) AS amount
        FROM premium_subscriptions
        WHERE "startedAt" >= date_trunc('month', ${now}::timestamptz AT TIME ZONE 'Europe/Paris') - interval '11 months'
        GROUP BY 1, 2
        ORDER BY 1, 2`,
    ]);
    return {
      totals: totals.map((row) => ({
        currency: row.currency,
        amount: row._sum.amount ?? 0,
      })),
      activeCount,
      expiredCount,
      monthly: monthly.map((row) => ({
        month: row.month,
        currency: row.currency,
        amount: Number(row.amount),
      })),
    };
  }
}
