import {
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UseGuards,
} from '@nestjs/common';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import {
  IssueParticipantTokenUseCase,
  LiveSdkNotConfiguredError,
  ParticipantUnavailableError,
} from '../../application/participant-token.use-case.js';

// Live partagé (ADR-031) : Jeton de Participant pour l'utilisateur connecté, demandé par le
// tokenProvider du SDK. Distinct des routes /lives de notre live shopping (ADR-029).
@Controller('live')
export class ParticipantTokenController {
  constructor(private readonly issueToken: IssueParticipantTokenUseCase) {}

  @Post('participant-token')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  async participantToken(@CurrentUser() user: AuthenticatedUser) {
    try {
      return { token: await this.issueToken.execute(user.id) };
    } catch (error) {
      if (error instanceof LiveSdkNotConfiguredError) {
        throw new ServiceUnavailableException(error.message);
      }
      if (error instanceof ParticipantUnavailableError) {
        throw new UnprocessableEntityException(error.message);
      }
      throw error;
    }
  }
}
