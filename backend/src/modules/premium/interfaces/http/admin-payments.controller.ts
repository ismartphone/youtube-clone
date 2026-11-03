import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { AdminGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { PaginationQuery } from '../../../videos/interfaces/http/list-videos.query.js';
import {
  GetRevenueStatsUseCase,
  ListSubscriptionsUseCase,
} from '../../application/premium.use-cases.js';

// Section Paiements du back-office (ADR-014). Ces routes renvoient des données personnelles
// (nom, email) et financières (montants, références de paiement) : elles sont réservées au
// rôle ADMIN, comme tout le back-office, par AdminGuard.
@Controller('admin/premium')
@UseGuards(AdminGuard)
export class AdminPaymentsController {
  constructor(
    private readonly listSubscriptions: ListSubscriptionsUseCase,
    private readonly getRevenueStats: GetRevenueStatsUseCase,
  ) {}

  // Abonnements Premium, du plus récent au plus ancien.
  @Get('subscriptions')
  async subscriptions(@Query() query: PaginationQuery) {
    const page = await this.listSubscriptions.execute(
      query.page,
      query.pageSize,
    );
    return {
      ...page,
      items: page.items.map((item) => ({
        id: item.id,
        user: { displayName: item.user.displayName, email: item.user.email },
        startedAt: item.startedAt,
        expiresAt: item.expiresAt,
        isActive: item.isActive,
        amount: item.amount,
        currency: item.currency,
        paymentReference: item.paymentReference,
      })),
    };
  }

  // Total encaissé (par devise), abonnements actifs et expirés, recettes des douze derniers mois.
  @Get('stats')
  stats() {
    return this.getRevenueStats.execute();
  }
}
