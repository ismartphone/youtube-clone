import { Controller, Get, UseGuards } from '@nestjs/common';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { OptionalCurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { OptionalJwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { presentAd } from '../../../ads/interfaces/http/ad.presenter.js';
import { ChooseAdUseCase } from '../../application/choose-ad.use-case.js';
import { ResolveViewerUseCase } from '../../application/resolve-viewer.use-case.js';

// Bandeau sponsorisé en haut du fil d'accueil (ADR-021). Ouvert aux visiteurs, qui ne sont
// jamais Premium ; ad vaut null pour un compte Premium ou sans aucune publicité.
@Controller('ads')
export class SponsoredController {
  constructor(
    private readonly resolveViewer: ResolveViewerUseCase,
    private readonly chooseAd: ChooseAdUseCase,
  ) {}

  @Get('sponsored')
  @UseGuards(OptionalJwtAuthGuard)
  async sponsored(@OptionalCurrentUser() user: AuthenticatedUser | undefined) {
    const viewer = await this.resolveViewer.execute(user?.id);
    const ad = await this.chooseAd.sponsored(viewer);
    return { ad: ad ? presentAd(ad) : null };
  }
}
