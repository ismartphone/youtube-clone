import { Transform } from 'class-transformer';
import { IsString, Length, Matches } from 'class-validator';
import { HANDLE_PATTERN, normalizeHandle } from '../../domain/channel.js';

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
}
