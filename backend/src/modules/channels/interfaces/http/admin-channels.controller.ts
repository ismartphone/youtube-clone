import {
  Body,
  Controller,
  Get,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Patch,
  UseGuards,
} from '@nestjs/common';
import { AdminGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { ListChannelsUseCase } from '../../application/list-channels.use-case.js';
import { UpdateChannelBannerUseCase } from '../../application/update-channel-banner.use-case.js';
import { ChannelNotFoundError } from '../../domain/channel.errors.js';
import { UpdateChannelBody } from './channel.bodies.js';
import { presentChannelSummary } from './channel.presenter.js';

// Back-office : liste des chaînes (rattachement d'une vidéo, page Chaînes) et bannière.
@Controller('admin/channels')
@UseGuards(AdminGuard)
export class AdminChannelsController {
  constructor(
    private readonly listChannels: ListChannelsUseCase,
    private readonly updateChannelBanner: UpdateChannelBannerUseCase,
  ) {}

  @Get()
  async list() {
    const channels = await this.listChannels.execute();
    return channels.map(presentChannelSummary);
  }

  @Patch(':id')
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: UpdateChannelBody,
  ) {
    const channel = await this.updateChannelBanner.execute(id, body.bannerUrl);
    if (!channel) {
      throw new NotFoundException(new ChannelNotFoundError().message);
    }
    return presentChannelSummary(channel);
  }
}
