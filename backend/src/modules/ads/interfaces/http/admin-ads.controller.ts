import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
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
import { IsString, Length } from 'class-validator';
import { tmpdir } from 'node:os';
import { AdminGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { ThumbnailUnavailableError } from '../../../videos/domain/thumbnail-generator.js';
import {
  InvalidVideoFileError,
  UploadUnavailableError,
} from '../../../videos/domain/video-upload.js';
import {
  FileTooLargeFilter,
  RemoveTempFileInterceptor,
  type ReceivedFile,
} from '../../../../shared/interfaces/http/file-upload.js';
import { ManageAdsUseCase } from '../../application/ads.use-cases.js';
import {
  AD_TITLE_MAX_LENGTH,
  AdNotFoundError,
  MAX_AD_UPLOAD_BYTES,
} from '../../domain/ad.js';
import { presentAdForAdmin } from './ad.presenter.js';

// Champ texte de l'import multipart, à côté du fichier (champ « file »).
export class ImportAdBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, AD_TITLE_MAX_LENGTH, {
    message: `Le titre doit faire entre 1 et ${AD_TITLE_MAX_LENGTH} caractères.`,
  })
  title: string;
}

// Publicités du back-office (ADR-021) : liste, import de fichier, suppression.
// Le garde passe avant la réception : seul un administrateur écrit sur le disque.
@Controller('admin/ads')
@UseGuards(AdminGuard)
export class AdminAdsController {
  constructor(private readonly ads: ManageAdsUseCase) {}

  @Get()
  async list() {
    return { items: (await this.ads.list()).map(presentAdForAdmin) };
  }

  @Post()
  @UseFilters(
    new FileTooLargeFilter(
      `La publicité dépasse la taille maximale de ${MAX_AD_UPLOAD_BYTES / 1024 / 1024} Mo.`,
    ),
  )
  @UseInterceptors(
    FileInterceptor('file', {
      // Nom aléatoire sans extension, généré par multer : celui envoyé n'est jamais repris.
      dest: tmpdir(),
      limits: { fileSize: MAX_AD_UPLOAD_BYTES, files: 1 },
    }),
    RemoveTempFileInterceptor,
  )
  async import(
    @UploadedFile() file: ReceivedFile | undefined,
    @Body() body: ImportAdBody,
  ) {
    if (!file) {
      throw new BadRequestException('Choisissez un fichier vidéo.');
    }
    try {
      return presentAdForAdmin(await this.ads.import(file.path, body.title));
    } catch (error) {
      if (error instanceof InvalidVideoFileError) {
        throw new UnprocessableEntityException(error.message);
      }
      // ffprobe ou FFmpeg absent : même réponse dans les deux cas.
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

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(@Param('id', ParseUUIDPipe) id: string) {
    try {
      await this.ads.remove(id);
    } catch (error) {
      if (error instanceof AdNotFoundError) {
        throw new NotFoundException(error.message);
      }
      throw error;
    }
  }
}
