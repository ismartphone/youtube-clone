import {
  Body,
  ConflictException,
  Controller,
  Delete,
  NotFoundException,
  Patch,
  Put,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UploadedFile,
  UseFilters,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { tmpdir } from 'node:os';
import {
  FileTooLargeFilter,
  RemoveTempFileInterceptor,
  type ReceivedFile,
} from '../../../../shared/interfaces/http/file-upload.js';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { CustomizeChannelUseCase } from '../../application/customize-channel.use-case.js';
import {
  ChannelImageUnavailableError,
  ChannelNotFoundError,
  HandleAlreadyUsedError,
  InvalidChannelImageError,
} from '../../domain/channel.errors.js';
import {
  CHANNEL_IMAGE_MAX_BYTES,
  type ChannelImageKind,
  type OwnChannel,
} from '../../domain/channel.js';
import { UpdateOwnChannelBody } from './channel.bodies.js';
import { presentChannelSummary } from './channel.presenter.js';

// Réception d'une image (champ « image ») : nom aléatoire généré par multer, 5 Mo au plus.
const IMAGE_UPLOAD = [
  FileInterceptor('image', {
    dest: tmpdir(),
    limits: { fileSize: CHANNEL_IMAGE_MAX_BYTES, files: 1 },
  }),
  RemoveTempFileInterceptor,
];
const IMAGE_TOO_LARGE = new FileTooLargeFilter(
  `L'image dépasse la taille maximale de ${CHANNEL_IMAGE_MAX_BYTES / 1024 / 1024} Mo.`,
);

function presentOwnChannel(channel: OwnChannel) {
  return {
    ...presentChannelSummary(channel),
    description: channel.description,
  };
}

/**
 * Personnalisation de sa propre chaîne depuis l'application (ADR-018). La chaîne est toujours
 * celle de l'utilisateur du token : aucun identifiant n'est reçu, personne ne modifie celle d'un
 * autre. Le garde d'authentification passe avant la réception : un anonyme n'écrit rien.
 */
@Controller('me/channel')
@UseGuards(JwtAuthGuard)
export class MyChannelController {
  constructor(private readonly customize: CustomizeChannelUseCase) {}

  @Patch()
  update(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: UpdateOwnChannelBody,
  ) {
    return this.run(() => this.customize.updateDetails(user.id, body));
  }

  @Put('photo')
  @UseFilters(IMAGE_TOO_LARGE)
  @UseInterceptors(...IMAGE_UPLOAD)
  setPhoto(
    @CurrentUser() user: AuthenticatedUser,
    @UploadedFile() image: ReceivedFile | undefined,
  ) {
    return this.setImage(user.id, 'photo', image);
  }

  @Delete('photo')
  removePhoto(@CurrentUser() user: AuthenticatedUser) {
    return this.run(() => this.customize.removeImage(user.id, 'photo'));
  }

  @Put('banner')
  @UseFilters(IMAGE_TOO_LARGE)
  @UseInterceptors(...IMAGE_UPLOAD)
  setBanner(
    @CurrentUser() user: AuthenticatedUser,
    @UploadedFile() image: ReceivedFile | undefined,
  ) {
    return this.setImage(user.id, 'banner', image);
  }

  @Delete('banner')
  removeBanner(@CurrentUser() user: AuthenticatedUser) {
    return this.run(() => this.customize.removeImage(user.id, 'banner'));
  }

  private setImage(
    userId: string,
    kind: ChannelImageKind,
    image: ReceivedFile | undefined,
  ) {
    if (!image) {
      throw new UnprocessableEntityException(
        new InvalidChannelImageError().message,
      );
    }
    return this.run(() => this.customize.setImage(userId, kind, image.path));
  }

  private async run(action: () => Promise<OwnChannel>) {
    try {
      return presentOwnChannel(await action());
    } catch (error) {
      if (error instanceof ChannelNotFoundError) {
        throw new NotFoundException(error.message);
      }
      if (error instanceof HandleAlreadyUsedError) {
        throw new ConflictException(error.message);
      }
      if (error instanceof InvalidChannelImageError) {
        throw new UnprocessableEntityException(error.message);
      }
      if (error instanceof ChannelImageUnavailableError) {
        throw new ServiceUnavailableException(error.message);
      }
      throw error;
    }
  }
}
