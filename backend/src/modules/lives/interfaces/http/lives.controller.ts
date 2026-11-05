import {
  BadRequestException,
  Body,
  ConflictException,
  Controller,
  ForbiddenException,
  Get,
  HttpCode,
  HttpException,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { Transform } from 'class-transformer';
import { IsIn, IsOptional, IsString, IsUUID, Length } from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import {
  LivesUseCases,
  NotLiveOwnerError,
} from '../../application/lives.use-cases.js';
import {
  CHAT_MESSAGE_MAX_LENGTH,
  ChannelRequiredError,
  ChatTooFastError,
  LIVE_TITLE_MAX_LENGTH,
  LiveAlreadyRunningError,
  LIVE_REACTIONS,
  LiveEndedError,
  NotEnoughFollowersError,
  ReactionsTooFastError,
  VideoNotEligibleError,
  type LiveReaction,
} from '../../domain/live.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

export class StartLiveBody {
  @Transform(trim)
  @IsString()
  @Length(1, LIVE_TITLE_MAX_LENGTH)
  title: string;

  @IsUUID()
  videoId: string;
}

export class LiveMessageBody {
  @Transform(trim)
  @IsString()
  @Length(1, CHAT_MESSAGE_MAX_LENGTH)
  content: string;
}

export class LiveReactionBody {
  @IsIn(LIVE_REACTIONS)
  emoji: LiveReaction;
}

export class RunningLivesQuery {
  @IsOptional()
  @IsUUID()
  channelId?: string;
}

// Erreurs du domaine traduites en réponses HTTP : le message est affiché tel quel par l'application.
function translate(error: unknown): never {
  if (
    error instanceof ChannelRequiredError ||
    error instanceof NotEnoughFollowersError ||
    error instanceof NotLiveOwnerError
  ) {
    throw new ForbiddenException(error.message);
  }
  if (error instanceof VideoNotEligibleError) {
    throw new BadRequestException(error.message);
  }
  if (
    error instanceof LiveAlreadyRunningError ||
    error instanceof LiveEndedError
  ) {
    throw new ConflictException(error.message);
  }
  if (
    error instanceof ChatTooFastError ||
    error instanceof ReactionsTooFastError
  ) {
    throw new HttpException(error.message, HttpStatus.TOO_MANY_REQUESTS);
  }
  throw error;
}

function found<T>(value: T | null): T {
  if (value === null) {
    throw new NotFoundException('Live introuvable');
  }
  return value;
}

// Démarrer un live : réservé aux comptes connectés qui ont une chaîne (ADR-029).
@Controller('me/lives')
@UseGuards(JwtAuthGuard)
export class MyLivesController {
  constructor(private readonly lives: LivesUseCases) {}

  @Post()
  async start(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: StartLiveBody,
  ) {
    try {
      return await this.lives.start(user.id, body.title, body.videoId);
    } catch (error) {
      translate(error);
    }
  }
}

// Lives : lecture publique (visiteurs compris), écriture réservée aux comptes connectés.
@Controller('lives')
export class LivesController {
  constructor(private readonly lives: LivesUseCases) {}

  // Lives en cours, du plus récent au plus ancien, avec leur nombre de spectateurs.
  @Get()
  async list(@Query() query: RunningLivesQuery) {
    return { items: await this.lives.listRunning(query.channelId) };
  }

  // État complet du live, en cours ou terminé : lu à l'arrivée et après une reconnexion.
  @Get(':id')
  async get(@Param('id', ParseUUIDPipe) id: string) {
    return found(await this.lives.get(id));
  }

  @Post(':id/end')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  async end(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    try {
      return found(await this.lives.end(user.id, id));
    } catch (error) {
      translate(error);
    }
  }

  // Derniers messages du chat, du plus récent au plus ancien.
  @Get(':id/messages')
  async messages(@Param('id', ParseUUIDPipe) id: string) {
    return { items: found(await this.lives.listMessages(id)) };
  }

  @Post(':id/messages')
  @UseGuards(JwtAuthGuard)
  async send(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: LiveMessageBody,
  ) {
    try {
      return found(await this.lives.send(user.id, id, body.content));
    } catch (error) {
      translate(error);
    }
  }

  // Réaction : diffusée à la salle, jamais stockée (ADR-029, D6).
  @Post(':id/reactions')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(JwtAuthGuard)
  async react(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: LiveReactionBody,
  ): Promise<void> {
    try {
      found(await this.lives.react(user.id, id, body.emoji));
    } catch (error) {
      translate(error);
    }
  }
}
