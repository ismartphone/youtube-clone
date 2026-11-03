import {
  ArgumentsHost,
  BadRequestException,
  Body,
  CallHandler,
  Catch,
  Controller,
  ExceptionFilter,
  ExecutionContext,
  ForbiddenException,
  HttpException,
  Injectable,
  NestInterceptor,
  PayloadTooLargeException,
  Post,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UploadedFile,
  UseFilters,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { BaseExceptionFilter } from '@nestjs/core';
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
import type { Request } from 'express';
import { rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { finalize, type Observable } from 'rxjs';
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
import { presentVideoDetail } from './video.presenter.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

const trimOrNull = ({ value }: { value: unknown }) => {
  if (typeof value !== 'string') {
    return value;
  }
  return value.trim() === '' ? null : value.trim();
};

// Champs texte de l'envoi multipart, à côté du fichier (champ « file »).
export class UploadVideoBody {
  @Transform(trim)
  @IsString()
  @Length(1, 100)
  title: string;

  @IsOptional()
  @Transform(trimOrNull)
  @IsString()
  @MaxLength(5000)
  description: string | null = null;

  @IsOptional()
  @Transform(trimOrNull)
  @IsUUID()
  categoryId: string | null = null;

  @IsIn(VIDEO_FORMATS)
  format: VideoFormat;
}

// Seuls le chemin du fichier temporaire et sa présence nous intéressent : le nom et le type
// annoncés par le client sont ignorés (ADR-015).
interface ReceivedFile {
  path: string;
}

// Le fichier temporaire est supprimé quoi qu'il arrive : refusé, corps invalide ou erreur.
// Accepté, il a déjà été déplacé dans le stockage et la suppression est sans effet.
@Injectable()
class RemoveTempFileInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context
      .switchToHttp()
      .getRequest<Request & { file?: ReceivedFile }>();
    return next.handle().pipe(
      finalize(() => {
        if (request.file) {
          void rm(request.file.path, { force: true });
        }
      }),
    );
  }
}

// Fichier trop gros, arrêté par multer pendant la réception : message en français.
@Catch(PayloadTooLargeException)
class FileTooLargeFilter
  extends BaseExceptionFilter
  implements ExceptionFilter
{
  catch(_exception: HttpException, host: ArgumentsHost) {
    super.catch(
      new PayloadTooLargeException(
        `La vidéo dépasse la taille maximale de ${MAX_UPLOAD_BYTES / 1024 / 1024} Mo.`,
      ),
      host,
    );
  }
}

// Publication depuis l'application mobile (ADR-015) : réservée au propriétaire d'une chaîne.
// Le garde d'authentification passe avant la réception : un anonyme n'écrit rien sur le disque.
@Controller('me/videos')
@UseGuards(JwtAuthGuard)
export class UploadController {
  constructor(private readonly uploadVideo: UploadVideoUseCase) {}

  @Post()
  @UseFilters(FileTooLargeFilter)
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
