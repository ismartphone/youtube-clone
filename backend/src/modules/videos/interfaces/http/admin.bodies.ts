import { Transform } from 'class-transformer';
import {
  IsBoolean,
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  IsUrl,
  IsUUID,
  Length,
  Max,
  MaxLength,
  Min,
} from 'class-validator';
import { VIDEO_FORMATS, type VideoFormat } from '../../domain/video.js';

// Corps des routes d'administration du catalogue, validés avant d'atteindre les cas d'usage.

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

// Texte facultatif : une chaîne vide devient null, pour effacer le champ.
const trimOrNull = ({ value }: { value: unknown }) => {
  if (typeof value !== 'string') {
    return value;
  }
  return value.trim() === '' ? null : value.trim();
};

// Les URL de développement pointent vers le serveur local (http://localhost:3000/...) :
// le domaine de premier niveau n'est pas exigé.
const URL_OPTIONS = {
  protocols: ['http', 'https'],
  require_protocol: true,
  require_tld: false,
};

// 12 heures : borne large, juste pour refuser les valeurs absurdes.
const MAX_DURATION_SECONDS = 12 * 60 * 60;

export class CreateVideoBody {
  @Transform(trim)
  @IsString()
  @Length(1, 100)
  title: string;

  @IsOptional()
  @Transform(trimOrNull)
  @IsString()
  @MaxLength(5000)
  description: string | null = null;

  @IsUUID()
  channelId: string;

  @IsOptional()
  @IsUUID()
  categoryId: string | null = null;

  @IsIn(VIDEO_FORMATS)
  format: VideoFormat;

  @IsBoolean()
  isPremium: boolean;

  @Transform(trim)
  @IsUrl(URL_OPTIONS)
  videoUrl: string;

  @Transform(trim)
  @IsUrl(URL_OPTIONS)
  thumbnailUrl: string;

  @IsInt()
  @Min(1)
  @Max(MAX_DURATION_SECONDS)
  durationSeconds: number;
}

// Modification partielle : seuls les champs envoyés changent. null efface description ou catégorie.
export class UpdateVideoBody {
  @IsOptional()
  @Transform(trim)
  @IsString()
  @Length(1, 100)
  title?: string;

  @IsOptional()
  @Transform(trimOrNull)
  @IsString()
  @MaxLength(5000)
  description?: string | null;

  @IsOptional()
  @IsUUID()
  channelId?: string;

  @IsOptional()
  @IsUUID()
  categoryId?: string | null;

  @IsOptional()
  @IsIn(VIDEO_FORMATS)
  format?: VideoFormat;

  @IsOptional()
  @IsBoolean()
  isPremium?: boolean;

  @IsOptional()
  @Transform(trim)
  @IsUrl(URL_OPTIONS)
  videoUrl?: string;

  @IsOptional()
  @Transform(trim)
  @IsUrl(URL_OPTIONS)
  thumbnailUrl?: string;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(MAX_DURATION_SECONDS)
  durationSeconds?: number;
}

export class CreateCategoryBody {
  @Transform(trim)
  @IsString()
  @Length(1, 50)
  name: string;
}
