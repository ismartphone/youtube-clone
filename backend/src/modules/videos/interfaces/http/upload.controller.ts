import {
  BadRequestException,
  Body,
  Controller,
  ForbiddenException,
  Post,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UploadedFile,
  UseFilters,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { Transform } from 'class-transformer';
import {
  IsIn,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  MaxLength,
} from 'class-validator';
import { tmpdir } from 'node:os';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { UploadVideoUseCase } from '../../application/upload-video.use-case.js';
import { UnknownCategoryError } from '../../domain/catalog.errors.js';
import { ThumbnailUnavailableError } from '../../domain/thumbnail-generator.js';
import { VIDEO_FORMATS, type VideoFormat } from '../../domain/video.js';
import {
  InvalidVideoFileError,
  MAX_UPLOAD_BYTES,
  NoChannelError,
  UploadUnavailableError,
} from '../../domain/video-upload.js';
import {
  FileTooLargeFilter,
  RemoveTempFileInterceptor,
  type ReceivedFile,
} from '../../../../shared/interfaces/http/file-upload.js';
import { presentVideoDetail } from './video.presenter.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

const trimOrNull = ({ value }: { value: unknown }) => {
  if (typeof value !== 'string') {
    return value;
  }
  return value.trim() === '' ? null : value.trim();
};

// Champs texte de l'envoi multipart, à côté du fichier (champ « file »). Description plus
// courte que depuis le back-office (1000 caractères contre 5000), et catégorie obligatoire.
export class UploadVideoBody {
  @Transform(trim)
  @IsString()
  @Length(1, 100, { message: 'Le titre doit faire entre 1 et 100 caractères.' })
  title: string;

  @IsOptional()
  @Transform(trimOrNull)
  @IsString()
  @MaxLength(1000, {
    message: 'La description fait 1000 caractères au plus.',
  })
  description: string | null = null;

  @IsUUID(undefined, { message: 'Choisissez une catégorie.' })
  categoryId: string;

  @IsIn(VIDEO_FORMATS, { message: 'Le format doit être SHORT ou LONG.' })
  format: VideoFormat;
}

// Publication depuis l'application mobile (ADR-015) : réservée au propriétaire d'une chaîne.
// Le garde d'authentification passe avant la réception : un anonyme n'écrit rien sur le disque.
@Controller('me/videos')
@UseGuards(JwtAuthGuard)
export class UploadController {
  constructor(private readonly uploadVideo: UploadVideoUseCase) {}

  @Post()
  @UseFilters(
    new FileTooLargeFilter(
      `La vidéo dépasse la taille maximale de ${MAX_UPLOAD_BYTES / 1024 / 1024} Mo.`,
    ),
  )
  @UseInterceptors(
    FileInterceptor('file', {
      // Nom aléatoire sans extension, généré par multer : celui du client n'est jamais repris.
      dest: tmpdir(),
      limits: { fileSize: MAX_UPLOAD_BYTES, files: 1 },
    }),
    RemoveTempFileInterceptor,
  )
  async upload(
    @CurrentUser() user: AuthenticatedUser,
    @UploadedFile() file: ReceivedFile | undefined,
    @Body() body: UploadVideoBody,
  ) {
    if (!file) {
      throw new BadRequestException('Choisissez une vidéo à publier.');
    }
    try {
      return presentVideoDetail(
        await this.uploadVideo.execute(user.id, file.path, body),
      );
    } catch (error) {
      if (error instanceof NoChannelError) {
        throw new ForbiddenException(error.message);
      }
      if (error instanceof InvalidVideoFileError) {
        throw new UnprocessableEntityException(error.message);
      }
      if (error instanceof UnknownCategoryError) {
        throw new BadRequestException(error.message);
      }
      // ffprobe ou FFmpeg absent (ADR-015) : même réponse dans les deux cas.
      if (
        error instanceof UploadUnavailableError ||
        error instanceof ThumbnailUnavailableError
      ) {
        throw new ServiceUnavailableException(
          new UploadUnavailableError().message,
        );
      }
      throw error;
    }
  }
}
