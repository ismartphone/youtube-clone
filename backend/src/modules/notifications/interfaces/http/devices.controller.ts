import {
  Body,
  Controller,
  Delete,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  UseGuards,
} from '@nestjs/common';
import { IsString, Length } from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { ManageDeviceTokensUseCase } from '../../application/device-tokens.use-case.js';

// Un jeton FCM fait environ 160 caractères ; la borne écarte seulement l'absurde.
const MAX_TOKEN_LENGTH = 4096;

// Corps de POST /me/devices : le jeton FCM de l'appareil.
export class DeviceTokenBody {
  @IsString()
  @Length(1, MAX_TOKEN_LENGTH)
  token: string;
}

// Appareils de l'utilisateur connecté qui reçoivent les notifications (ADR-020).
@Controller('me/devices')
@UseGuards(JwtAuthGuard)
export class DevicesController {
  constructor(private readonly devices: ManageDeviceTokensUseCase) {}

  @Post()
  @HttpCode(HttpStatus.NO_CONTENT)
  async register(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: DeviceTokenBody,
  ) {
    await this.devices.register(user.id, body.token);
  }

  // À la déconnexion. Idempotent : un jeton inconnu ne change rien.
  @Delete(':token')
  @HttpCode(HttpStatus.NO_CONTENT)
  async unregister(
    @CurrentUser() user: AuthenticatedUser,
    @Param('token') token: string,
  ) {
    await this.devices.unregister(user.id, token);
  }
}
