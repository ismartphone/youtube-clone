import {
  Body,
  Controller,
  ForbiddenException,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  UnprocessableEntityException,
  UseGuards,
} from '@nestjs/common';
import { ArrayMaxSize, IsArray, IsUUID } from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import {
  AuthorizeDownloadUseCase,
  CheckDownloadsUseCase,
  NotDownloadableError,
  PremiumRequiredError,
} from '../../application/download.use-cases.js';
import { PREMIUM_REQUIRED } from './videos.controller.js';

// Au plus 20 vidéos téléchargées par téléphone (décision 11) ; un peu de marge pour la liste.
export class CheckDownloadsBody {
  @IsArray()
  @ArrayMaxSize(50)
  @IsUUID('all', { each: true })
  videoIds: string[];
}

/** Téléchargements pour la lecture hors ligne, réservés aux abonnés Premium (ADR-019, partie 2). */
@Controller()
@UseGuards(JwtAuthGuard)
export class DownloadsController {
  constructor(
    private readonly authorize: AuthorizeDownloadUseCase,
    private readonly check: CheckDownloadsUseCase,
  ) {}

  // Remet l'adresse du fichier à télécharger, après vérification du Premium et de l'accès.
  @Post('videos/:id/download')
  @HttpCode(HttpStatus.OK)
  async download(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    try {
      const grant = await this.authorize.execute(user.id, id);
      if (!grant) {
        throw new NotFoundException('Vidéo introuvable');
      }
      return grant;
    } catch (error) {
      if (error instanceof PremiumRequiredError) {
        throw new ForbiddenException({
          statusCode: HttpStatus.FORBIDDEN,
          error: PREMIUM_REQUIRED,
          message: error.message,
        });
      }
      if (error instanceof NotDownloadableError) {
        throw new UnprocessableEntityException(error.message);
      }
      throw error;
    }
  }

  // Revalidation des vidéos téléchargées : lesquelles restent lisibles, jusqu'à quand.
  @Post('me/downloads/check')
  @HttpCode(HttpStatus.OK)
  checkDownloads(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: CheckDownloadsBody,
  ) {
    return this.check.execute(user.id, body.videoIds);
  }
}
