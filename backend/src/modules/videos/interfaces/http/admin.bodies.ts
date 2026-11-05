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
  ValidateIf,
} from 'class-validator';
import { IsNotLocalUrl } from '../../../../shared/interfaces/http/local-url.js';
import { VIDEO_FORMATS, type VideoFormat } from '../../domain/video.js';
import {
  VIDEO_VISIBILITIES,
  type VideoVisibility,
} from '../../domain/visibility.js';

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

// Miniature déjà générée par le serveur : chemin relatif, renvoyé tel quel par le formulaire.
const GENERATED_THUMBNAIL = /^\/media\/thumbnails\/[0-9a-f-]{36}\.jpg$/;

// Seule une URL externe est validée ; absente, null ou déjà générée, elle est acceptée telle quelle.
function isExternalThumbnail(body: { thumbnailUrl?: unknown }): boolean {
  const value = body.thumbnailUrl;
  return (
    value !== undefined &&
    value !== null &&
    !(typeof value === 'string' && GENERATED_THUMBNAIL.test(value))
  );
}

// Vidéo hébergée chez nous (ADR-002) : chemin relatif vers le stockage local, servi sous /media.
const LOCAL_VIDEO = /^\/media\/videos\/[\w.-]+\.mp4$/;

// Une vidéo est soit une URL http(s), soit un fichier de notre stockage.
function isExternalVideo(body: { videoUrl?: unknown }): boolean {
  const value = body.videoUrl;
  return !(typeof value === 'string' && LOCAL_VIDEO.test(value));
}

// 12 heures : borne large, juste pour refuser les valeurs absurdes.
const MAX_DURATION_SECONDS = 12 * 60 * 60;

export class CreateVideoBody {
  // Réglages de publication (ADR-016) ; absents : publique, commentaires activés.
  @IsOptional()
  @IsIn(VIDEO_VISIBILITIES)
  visibility?: VideoVisibility;

  @IsOptional()
  @IsBoolean()
  commentsEnabled?: boolean;

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
  @ValidateIf(isExternalVideo)
  @IsUrl(URL_OPTIONS)
  @IsNotLocalUrl()
  videoUrl: string;

  // Facultative : absente ou vide, le serveur génère une image extraite de la vidéo.
  @Transform(trimOrNull)
  @ValidateIf(isExternalThumbnail)
  @IsUrl(URL_OPTIONS)
  @IsNotLocalUrl()
  thumbnailUrl: string | null = null;

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

  // Réglages modifiables après publication depuis le back-office seulement (ADR-016).
  @IsOptional()
  @IsIn(VIDEO_VISIBILITIES)
  visibility?: VideoVisibility;

  @IsOptional()
  @IsBoolean()
  commentsEnabled?: boolean;

  @Transform(trim)
  @ValidateIf(
    (body: { videoUrl?: unknown }) =>
      body.videoUrl !== undefined && isExternalVideo(body),
  )
  @IsUrl(URL_OPTIONS)
  @IsNotLocalUrl()
  videoUrl?: string;

  // Absente : inchangée. Vide ou null : le serveur génère une nouvelle image.
  @Transform(trimOrNull)
  @ValidateIf(isExternalThumbnail)
  @IsUrl(URL_OPTIONS)
  @IsNotLocalUrl()
  thumbnailUrl?: string | null;

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
