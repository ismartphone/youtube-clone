import {
  BadRequestException,
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { Transform } from 'class-transformer';
import {
  IsOptional,
  IsString,
  IsUUID,
  Length,
  MaxLength,
} from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { PaginationQuery } from '../../../videos/interfaces/http/list-videos.query.js';
import { MessagingUseCases } from '../../application/messaging.use-cases.js';
import {
  MESSAGE_MAX_LENGTH,
  SelfConversationError,
  type ConversationSummary,
} from '../../domain/conversation.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

export class StartConversationBody {
  @IsUUID()
  userId: string;
}

// Texte, vidéo partagée (ADR-025, étape 2), ou les deux : le texte est facultatif avec une vidéo.
// Un message sans l'un ni l'autre est refusé par le contrôleur.
export class SendMessageBody {
  @Transform(trim)
  @IsOptional()
  @IsString()
  @MaxLength(MESSAGE_MAX_LENGTH)
  content: string = '';

  @IsOptional()
  @IsUUID()
  videoId?: string;
}

export class SearchPeopleQuery {
  @Transform(trim)
  @IsString()
  @Length(1, 100)
  q: string;
}

// Messagerie (ADR-025), réservée aux comptes connectés. Les messages et la conversation ont
// la même forme en REST et dans l'événement message:new.
@Controller('me/conversations')
@UseGuards(JwtAuthGuard)
export class MyConversationsController {
  constructor(private readonly messaging: MessagingUseCases) {}

  // Triées par dernier message ; les conversations encore vides n'apparaissent pas.
  @Get()
  async list(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: PaginationQuery,
  ) {
    return this.messaging.listConversations(user.id, query);
  }

  // Total des messages non lus, pour la pastille de la barre du haut.
  @Get('unread-count')
  async unreadCount(@CurrentUser() user: AuthenticatedUser) {
    return { unreadCount: await this.messaging.unreadCount(user.id) };
  }

  // Conversation existante avec cet utilisateur, ou nouvelle.
  @Post()
  @HttpCode(HttpStatus.OK)
  async start(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: StartConversationBody,
  ): Promise<ConversationSummary> {
    let conversation;
    try {
      conversation = await this.messaging.start(user.id, body.userId);
    } catch (error) {
      if (error instanceof SelfConversationError) {
        throw new BadRequestException(error.message);
      }
      throw error;
    }
    if (!conversation) {
      throw new NotFoundException('Utilisateur introuvable');
    }
    return conversation;
  }
}

// Une conversation : pour un tiers, elle n'existe pas (404), comme une vidéo privée (ADR-016).
@Controller('conversations/:id')
@UseGuards(JwtAuthGuard)
export class ConversationController {
  constructor(private readonly messaging: MessagingUseCases) {}

  @Get()
  async get(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return found(await this.messaging.getConversation(id, user.id));
  }

  // Du plus récent au plus ancien.
  @Get('messages')
  async messages(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Query() query: PaginationQuery,
  ) {
    return found(await this.messaging.listMessages(id, user.id, query));
  }

  @Post('messages')
  async send(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: SendMessageBody,
  ) {
    if (!body.content && !body.videoId) {
      throw new BadRequestException('Le message est vide.');
    }
    return found(
      await this.messaging.send(
        id,
        user.id,
        body.content,
        body.videoId ?? null,
      ),
    );
  }

  // Marquage comme lu, à l'ouverture et à chaque message reçu écran ouvert.
  @Post('read')
  @HttpCode(HttpStatus.NO_CONTENT)
  async read(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    found((await this.messaging.markRead(id, user.id)) || null);
  }
}

// Recherche d'un destinataire sur le nom affiché (ADR-025, choix de périmètre) : chaque
// résultat montre l'avatar, le nom et le @handle de la chaîne s'il y en a une.
@Controller('users')
@UseGuards(JwtAuthGuard)
export class UsersSearchController {
  constructor(private readonly messaging: MessagingUseCases) {}

  @Get('search')
  async search(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: SearchPeopleQuery,
  ) {
    return { items: await this.messaging.searchPeople(query.q, user.id) };
  }
}

function found<T>(value: T | null): T {
  if (value === null) {
    throw new NotFoundException('Conversation introuvable');
  }
  return value;
}
