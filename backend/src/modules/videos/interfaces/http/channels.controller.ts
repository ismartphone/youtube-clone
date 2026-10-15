import {
  Controller,
  Get,
  NotFoundException,
  Param,
  ParseUUIDPipe,
} from '@nestjs/common';
import { GetChannelUseCase } from '../../application/get-channel.use-case.js';
import { ANONYMOUS_VIEWER } from '../../domain/video.js';
import { presentChannel } from './video.presenter.js';

// Page de chaîne, servie par le module videos tant que le module channels n'existe pas.
@Controller('channels')
export class ChannelsController {
  constructor(private readonly getChannel: GetChannelUseCase) {}

  @Get(':id')
  async detail(@Param('id', ParseUUIDPipe) id: string) {
    const channel = await this.getChannel.execute(id, ANONYMOUS_VIEWER);
    if (!channel) {
      throw new NotFoundException('Chaîne introuvable');
    }
    return presentChannel(channel);
  }
}
