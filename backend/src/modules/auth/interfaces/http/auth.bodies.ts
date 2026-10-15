import { Transform } from 'class-transformer';
import { IsEmail, IsJWT, IsString, Length, MaxLength } from 'class-validator';

// Corps des requêtes du module auth, validés avant d'atteindre les cas d'usage.

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

export class RegisterBody {
  @Transform(trim)
  @IsEmail()
  @MaxLength(254)
  email: string;

  // Borne haute pour éviter de hacher des chaînes démesurées.
  @IsString()
  @Length(8, 128)
  password: string;

  @Transform(trim)
  @IsString()
  @Length(1, 50)
  displayName: string;
}

export class LoginBody {
  @Transform(trim)
  @IsEmail()
  email: string;

  @IsString()
  @MaxLength(128)
  password: string;
}

export class RefreshBody {
  @IsJWT()
  refreshToken: string;
}
