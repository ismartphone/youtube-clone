import {
  Body,
  Controller,
  Get,
  Inject,
  Patch,
  UseGuards,
} from '@nestjs/common';
import { IsBoolean } from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import {
  MESSAGE_RECIPIENT_REPOSITORY,
  type MessageRecipientRepository,
} from '../../domain/ports.js';

export class NotificationSettingsBody {
  @IsBoolean()
  messages: boolean;
}

// Réglage « Notifications de messages » de Paramètres (ADR-025, étape 3), stocké côté serveur :
// c'est le serveur qui décide d'envoyer, et le réglage suit le compte sur tous ses appareils.
@Controller('me/notification-settings')
@UseGuards(JwtAuthGuard)
export class NotificationSettingsController {
  constructor(
    @Inject(MESSAGE_RECIPIENT_REPOSITORY)
    private readonly recipients: MessageRecipientRepository,
  ) {}

  @Get()
  async get(@CurrentUser() user: AuthenticatedUser) {
    return { messages: await this.recipients.isEnabled(user.id) };
  }

  @Patch()
  async update(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: NotificationSettingsBody,
  ) {
    await this.recipients.setEnabled(user.id, body.messages);
    return { messages: body.messages };
  }
}
