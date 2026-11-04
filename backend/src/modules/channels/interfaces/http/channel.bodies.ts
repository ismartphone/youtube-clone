import { Transform } from 'class-transformer';
import {
  IsIn,
  IsOptional,
  IsString,
  IsUrl,
  Length,
  Matches,
  MaxLength,
  ValidateIf,
} from 'class-validator';
import {
  CHANNEL_DESCRIPTION_MAX_LENGTH,
  CHANNEL_NAME_MAX_LENGTH,
  HANDLE_PATTERN,
  NOTIFICATION_LEVELS,
  type NotificationLevel,
  normalizeHandle,
} from '../../domain/channel.js';

// Bannière de notre stockage : chemin relatif servi sous /media (ADR-011).
const LOCAL_BANNER = /^\/media\/banners\/[\w.-]+\.(jpg|jpeg|png|webp)$/;

// null efface la bannière, un chemin local est accepté tel quel : seule une URL externe est
// vérifiée. Un champ absent échoue à cette vérification, il doit donc être envoyé.
function isExternalBanner(body: { bannerUrl?: unknown }): boolean {
  const value = body.bannerUrl;
  return (
    value !== null && !(typeof value === 'string' && LOCAL_BANNER.test(value))
  );
}

// Corps de PATCH /admin/channels/:id. Une chaîne vide efface la bannière.
export class UpdateChannelBody {
  @Transform(({ value }: { value: unknown }) => {
    if (typeof value !== 'string') {
      return value;
    }
    return value.trim() === '' ? null : value.trim();
  })
  @ValidateIf(isExternalBanner)
  @IsUrl(
    {
      protocols: ['http', 'https'],
      require_protocol: true,
      require_tld: false,
    },
    {
      message:
        'La bannière doit être une URL http(s) ou un chemin /media/banners/...',
    },
  )
  bannerUrl: string | null;
}

// Corps de POST /channels, validé avant d'atteindre le cas d'usage.
export class CreateChannelBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, 50)
  name: string;

  // Normalisé avant validation : « @Studio.Libre » devient « studio.libre ».
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? normalizeHandle(value) : value,
  )
  @IsString()
  @Matches(HANDLE_PATTERN, {
    message:
      'Le handle doit contenir 3 à 30 caractères : lettres, chiffres, points, tirets ou tirets bas',
  })
  handle: string;

  // Facultative ; une chaîne vide est enregistrée comme absente.
  @IsOptional()
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() || null : value,
  )
  @IsString()
  @MaxLength(1000, { message: 'La description fait 1000 caractères au plus.' })
  description: string | null = null;
}

// Corps de PATCH /me/channel (ADR-018) : un champ absent n'est pas modifié. Mêmes règles qu'à la
// création ; une description vide est effacée.
export class UpdateOwnChannelBody {
  @IsOptional()
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, CHANNEL_NAME_MAX_LENGTH, {
    message: `Le nom doit faire entre 1 et ${CHANNEL_NAME_MAX_LENGTH} caractères.`,
  })
  name?: string;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? normalizeHandle(value) : value,
  )
  @IsString()
  @Matches(HANDLE_PATTERN, {
    message:
      'Le handle doit contenir 3 à 30 caractères : lettres, chiffres, points, tirets ou tirets bas',
  })
  handle?: string;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() || null : value,
  )
  @ValidateIf((_, value) => value !== null)
  @IsString()
  @MaxLength(CHANNEL_DESCRIPTION_MAX_LENGTH, {
    message: `La description fait ${CHANNEL_DESCRIPTION_MAX_LENGTH} caractères au plus.`,
  })
  description?: string | null;
}

// Corps de PATCH /channels/:id/follow : la cloche de l'abonnement (ADR-020).
export class NotificationLevelBody {
  @IsIn(NOTIFICATION_LEVELS)
  notificationLevel!: NotificationLevel;
}
