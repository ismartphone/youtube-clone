import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { ListVideosUseCase } from '../../application/list-videos.use-case.js';
import { ANONYMOUS_VIEWER } from '../../domain/video.js';
import { PaginationQuery } from './list-videos.query.js';
import { presentVideoSummary } from './video.presenter.js';

@Controller('me')
@UseGuards(JwtAuthGuard)
export class FeedController {
  constructor(private readonly listVideos: ListVideosUseCase) {}

  // Fil Abonnements : vidéos des chaînes suivies, de la plus récente à la plus ancienne.
  @Get('feed')
  async feed(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: PaginationQuery,
  ) {
    // Le statut Premium n'est pas encore géré : le spectateur reste traité comme non Premium.
    const page = await this.listVideos.execute(
      { followedBy: user.id },
      { page: query.page, pageSize: query.pageSize },
      ANONYMOUS_VIEWER,
    );
    return { ...page, items: page.items.map(presentVideoSummary) };
  }
}
