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
  Patch,
  Post,
  Put,
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
  IsOptional,
  IsString,
  IsUrl,
  Length,
  MaxLength,
} from 'class-validator';
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
import {
  ChannelImageUnavailableError,
  InvalidChannelImageError,
} from '../../../channels/domain/channel.errors.js';
import { CHANNEL_IMAGE_MAX_BYTES } from '../../../channels/domain/channel.js';
import { ManageAdsUseCase } from '../../application/ads.use-cases.js';
import {
  AD_ADVERTISER_MAX_LENGTH,
  AD_CTA_MAX_LENGTH,
  AD_DESCRIPTION_MAX_LENGTH,
  AD_TITLE_MAX_LENGTH,
  AD_URL_MAX_LENGTH,
  AdNotFoundError,
  MAX_AD_UPLOAD_BYTES,
} from '../../domain/ad.js';
import { presentAdForAdmin } from './ad.presenter.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

const TITLE_MESSAGE = `Le titre doit faire entre 1 et ${AD_TITLE_MAX_LENGTH} caractères.`;
const URL_MESSAGE =
  "Saisissez l'adresse complète du site, par exemple https://www.exemple.fr.";
const URL_OPTIONS = { protocols: ['http', 'https'], require_protocol: true };
const ADVERTISER_MESSAGE = `Le nom de l'annonceur doit faire entre 1 et ${AD_ADVERTISER_MAX_LENGTH} caractères.`;
const DESCRIPTION_MESSAGE = `La description doit faire entre 1 et ${AD_DESCRIPTION_MAX_LENGTH} caractères.`;
const CTA_MESSAGE = `Le libellé du bouton doit faire entre 1 et ${AD_CTA_MAX_LENGTH} caractères.`;

// Champs texte de l'import multipart, à côté du fichier (champ « file »).
export class ImportAdBody {
  @Transform(trim)
  @IsString()
  @Length(1, AD_TITLE_MAX_LENGTH, { message: TITLE_MESSAGE })
  title: string;

  @Transform(trim)
  @IsUrl(URL_OPTIONS, { message: URL_MESSAGE })
  @MaxLength(AD_URL_MAX_LENGTH)
  destinationUrl: string;

  @Transform(trim)
  @IsString()
  @Length(1, AD_ADVERTISER_MAX_LENGTH, { message: ADVERTISER_MESSAGE })
  advertiserName: string;

  @Transform(trim)
  @IsString()
  @Length(1, AD_DESCRIPTION_MAX_LENGTH, { message: DESCRIPTION_MESSAGE })
  description: string;

  // Vide ou absent : « Visiter le site ».
  @IsOptional()
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' && value.trim() === ''
      ? undefined
      : trim({ value }),
  )
  @IsString()
  @Length(1, AD_CTA_MAX_LENGTH, { message: CTA_MESSAGE })
  ctaLabel?: string;
}

// Corps de PATCH /admin/ads/:id : un champ absent n'est pas modifié.
export class UpdateAdBody {
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, AD_TITLE_MAX_LENGTH, { message: TITLE_MESSAGE })
  title?: string;

  @IsOptional()
  @Transform(trim)
  @IsUrl(URL_OPTIONS, { message: URL_MESSAGE })
  @MaxLength(AD_URL_MAX_LENGTH)
  destinationUrl?: string;

  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, AD_ADVERTISER_MAX_LENGTH, { message: ADVERTISER_MESSAGE })
  advertiserName?: string;

  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, AD_DESCRIPTION_MAX_LENGTH, { message: DESCRIPTION_MESSAGE })
  description?: string;

  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, AD_CTA_MAX_LENGTH, { message: CTA_MESSAGE })
  ctaLabel?: string;
}

const LOGO_TOO_LARGE = new FileTooLargeFilter(
  `Le logo dépasse la taille maximale de ${CHANNEL_IMAGE_MAX_BYTES / 1024 / 1024} Mo.`,
);

// Publicités du back-office (ADR-021) : liste, import de fichier, champs de la carte du
// bandeau sponsorisé et logo de l'annonceur, suppression.
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
      return presentAdForAdmin(
        await this.ads.import(file.path, {
          title: body.title,
          destinationUrl: body.destinationUrl,
          advertiserName: body.advertiserName,
          description: body.description,
          ctaLabel: body.ctaLabel,
        }),
      );
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

  @Patch(':id')
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: UpdateAdBody,
  ) {
    try {
      return presentAdForAdmin(
        await this.ads.update(id, {
          title: body.title,
          destinationUrl: body.destinationUrl,
          advertiserName: body.advertiserName,
          description: body.description,
          ctaLabel: body.ctaLabel,
        }),
      );
    } catch (error) {
      if (error instanceof AdNotFoundError) {
        throw new NotFoundException(error.message);
      }
      throw error;
    }
  }

  // Logo de l'annonceur (champ « logo »), validé et recadré comme la photo de chaîne.
  @Put(':id/logo')
  @UseFilters(LOGO_TOO_LARGE)
  @UseInterceptors(
    FileInterceptor('logo', {
      dest: tmpdir(),
      limits: { fileSize: CHANNEL_IMAGE_MAX_BYTES, files: 1 },
    }),
    RemoveTempFileInterceptor,
  )
  async setLogo(
    @Param('id', ParseUUIDPipe) id: string,
    @UploadedFile() logo: ReceivedFile | undefined,
  ) {
    if (!logo) {
      throw new BadRequestException('Choisissez une image.');
    }
    try {
      return presentAdForAdmin(await this.ads.setLogo(id, logo.path));
    } catch (error) {
      throw this.logoError(error);
    }
  }

  @Delete(':id/logo')
  async removeLogo(@Param('id', ParseUUIDPipe) id: string) {
    try {
      return presentAdForAdmin(await this.ads.removeLogo(id));
    } catch (error) {
      throw this.logoError(error);
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

  private logoError(error: unknown): unknown {
    if (error instanceof AdNotFoundError) {
      return new NotFoundException(error.message);
    }
    if (error instanceof InvalidChannelImageError) {
      return new UnprocessableEntityException(error.message);
    }
    if (error instanceof ChannelImageUnavailableError) {
      return new ServiceUnavailableException(error.message);
    }
    return error;
  }
}
