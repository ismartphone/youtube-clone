import { Controller, Get, UseGuards } from '@nestjs/common';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { ListSubscriptionsUseCase } from '../../application/list-subscriptions.use-case.js';
import { presentChannelSummary } from './channel.presenter.js';

// Abonnements gratuits (FOLLOW) de l'utilisateur connecté, à ne pas confondre avec l'offre Premium.
@Controller('me')
@UseGuards(JwtAuthGuard)
export class SubscriptionsController {
  constructor(private readonly listSubscriptions: ListSubscriptionsUseCase) {}

  @Get('subscriptions')
  async list(@CurrentUser() user: AuthenticatedUser) {
    const channels = await this.listSubscriptions.execute(user.id);
    return channels.map(presentChannelSummary);
  }
}
