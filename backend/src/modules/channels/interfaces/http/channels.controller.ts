import {
  BadRequestException,
  Body,
  ConflictException,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  UseGuards,
} from '@nestjs/common';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import {
  CurrentUser,
  OptionalCurrentUser,
} from '../../../auth/interfaces/http/current-user.decorator.js';
import {
  JwtAuthGuard,
  OptionalJwtAuthGuard,
} from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { GetChannelUseCase } from '../../../videos/application/get-channel.use-case.js';
import { ANONYMOUS_VIEWER } from '../../../videos/domain/video.js';
import { CreateChannelUseCase } from '../../application/create-channel.use-case.js';
import { FollowChannelUseCase } from '../../application/follow-channel.use-case.js';
import { GetFollowStatusUseCase } from '../../application/get-follow-status.use-case.js';
import {
  CannotFollowOwnChannelError,
  ChannelAlreadyOwnedError,
  ChannelNotFoundError,
  HandleAlreadyUsedError,
} from '../../domain/channel.errors.js';
import { CreateChannelBody } from './channel.bodies.js';
import {
  presentChannelPage,
  presentChannelSummary,
  presentFollowStatus,
} from './channel.presenter.js';

@Controller('channels')
export class ChannelsController {
  constructor(
    private readonly createChannel: CreateChannelUseCase,
    private readonly getChannel: GetChannelUseCase,
    private readonly getFollowStatus: GetFollowStatusUseCase,
    private readonly followChannel: FollowChannelUseCase,
  ) {}

  @Post()
  @UseGuards(JwtAuthGuard)
  async create(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: CreateChannelBody,
  ) {
    try {
      return presentChannelSummary(
        await this.createChannel.execute(user.id, body),
      );
    } catch (error) {
      if (
        error instanceof ChannelAlreadyOwnedError ||
        error instanceof HandleAlreadyUsedError
      ) {
        throw new ConflictException(error.message);
      }
      throw error;
    }
  }

  // Ouverte aux visiteurs ; isFollowing n'est vrai que pour un utilisateur connecté et abonné.
  @Get(':id')
  @UseGuards(OptionalJwtAuthGuard)
  async detail(
    @Param('id', ParseUUIDPipe) id: string,
    @OptionalCurrentUser() user: AuthenticatedUser | undefined,
  ) {
    // Les vidéos viennent du module videos, où le statut Premium n'est pas encore géré.
    const [channel, status] = await Promise.all([
      this.getChannel.execute(id, ANONYMOUS_VIEWER),
      this.getFollowStatus.execute(id, user?.id),
    ]);
    if (!channel) {
      throw new NotFoundException(new ChannelNotFoundError().message);
    }
    return presentChannelPage(channel, status);
  }

  @Post(':id/follow')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  follow(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.setFollow(user.id, id, true);
  }

  @Delete(':id/follow')
  @UseGuards(JwtAuthGuard)
  unfollow(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.setFollow(user.id, id, false);
  }

  private async setFollow(userId: string, channelId: string, follow: boolean) {
    try {
      return presentFollowStatus(
        await this.followChannel.execute(userId, channelId, follow),
      );
    } catch (error) {
      if (error instanceof ChannelNotFoundError) {
        throw new NotFoundException(error.message);
      }
      if (error instanceof CannotFollowOwnChannelError) {
        throw new BadRequestException(error.message);
      }
      throw error;
    }
  }
}
