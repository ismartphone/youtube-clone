import { Controller, Get, UseGuards } from '@nestjs/common';
import { AdminGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { ListChannelsUseCase } from '../../application/list-channels.use-case.js';
import { presentChannelSummary } from './channel.presenter.js';

// Back-office : liste des chaînes pour rattacher une vidéo à sa chaîne.
@Controller('admin/channels')
@UseGuards(AdminGuard)
export class AdminChannelsController {
  constructor(private readonly listChannels: ListChannelsUseCase) {}

  @Get()
  async list() {
    const channels = await this.listChannels.execute();
    return channels.map(presentChannelSummary);
  }
}
