import { JwtService } from '@nestjs/jwt';
import {
  PARTICIPANT_TOKEN_TTL_SECONDS,
  type ParticipantIdentity,
  type ParticipantTokenSigner,
} from '../domain/participant-token.js';

// JWT HS256 signé avec le secret de notre App membre, transmis en privé par le serveur commun
// (LIVE_SDK_SECRET). iss = identifiant de notre App membre (LIVE_SDK_APP_ID) ; iat et exp sont
// posés par la bibliothèque, exp 5 minutes plus tard.
export class JwtParticipantTokenSigner implements ParticipantTokenSigner {
  private readonly jwt = new JwtService({});

  constructor(
    private readonly appId: string,
    private readonly secret: string,
  ) {}

  sign(identity: ParticipantIdentity): Promise<string> {
    const claims: Record<string, string> = { name: identity.name };
    if (identity.picture) claims.picture = identity.picture;
    return this.jwt.signAsync(claims, {
      secret: this.secret,
      algorithm: 'HS256',
      issuer: this.appId,
      subject: identity.userId,
      expiresIn: PARTICIPANT_TOKEN_TTL_SECONDS,
    });
  }
}
