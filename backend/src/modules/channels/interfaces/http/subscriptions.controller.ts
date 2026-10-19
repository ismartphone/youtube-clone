import { Controller, Get, UseGuards } from '@nestjs/common';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { GetOwnChannelUseCase } from '../../application/get-own-channel.use-case.js';
import { ListSubscriptionsUseCase } from '../../application/list-subscriptions.use-case.js';
import { presentChannelSummary } from './channel.presenter.js';

// Routes de l'utilisateur connecté liées aux chaînes : sa propre chaîne, et ses abonnements
// gratuits (FOLLOW), à ne pas confondre avec l'offre Premium.
@Controller('me')
@UseGuards(JwtAuthGuard)
export class SubscriptionsController {
  constructor(
    private readonly listSubscriptions: ListSubscriptionsUseCase,
    private readonly getOwnChannel: GetOwnChannelUseCase,
  ) {}

  @Get('subscriptions')
  async list(@CurrentUser() user: AuthenticatedUser) {
    const channels = await this.listSubscriptions.execute(user.id);
    return channels.map(presentChannelSummary);
  }

  // Toujours 200 : { channel: null } si l'utilisateur n'a pas encore créé de chaîne.
  @Get('channel')
  async channel(@CurrentUser() user: AuthenticatedUser) {
    const channel = await this.getOwnChannel.execute(user.id);
    return { channel: channel ? presentChannelSummary(channel) : null };
  }
}
