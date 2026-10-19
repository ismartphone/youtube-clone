import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import {
  ListLikedVideosUseCase,
  ListWatchHistoryUseCase,
} from '../../application/list-personal-videos.use-case.js';
import { ListVideosUseCase } from '../../application/list-videos.use-case.js';
import type { VideoView } from '../../application/video-view.js';
import { ANONYMOUS_VIEWER } from '../../domain/video.js';
import type { Page } from '../../domain/video.repository.js';
import { PaginationQuery } from './list-videos.query.js';
import { presentVideoSummary } from './video.presenter.js';

// Listes de vidéos propres à l'utilisateur connecté : fil Abonnements, historique, vidéos aimées.
// Le statut Premium n'est pas encore géré : le spectateur reste traité comme non Premium.
@Controller('me')
@UseGuards(JwtAuthGuard)
export class FeedController {
  constructor(
    private readonly listVideos: ListVideosUseCase,
    private readonly listHistory: ListWatchHistoryUseCase,
    private readonly listLiked: ListLikedVideosUseCase,
  ) {}

  // Fil Abonnements : vidéos des chaînes suivies, de la plus récente à la plus ancienne.
  @Get('feed')
  async feed(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: PaginationQuery,
  ) {
    return presentPage(
      await this.listVideos.execute(
        { followedBy: user.id },
        { page: query.page, pageSize: query.pageSize },
        ANONYMOUS_VIEWER,
      ),
    );
  }

  // Historique : de la vidéo regardée le plus récemment à la plus ancienne.
  @Get('history')
  async history(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: PaginationQuery,
  ) {
    return presentPage(
      await this.listHistory.execute(
        user.id,
        { page: query.page, pageSize: query.pageSize },
        ANONYMOUS_VIEWER,
      ),
    );
  }

  // Vidéos aimées : de la plus récemment aimée à la plus ancienne.
  @Get('liked')
  async liked(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: PaginationQuery,
  ) {
    return presentPage(
      await this.listLiked.execute(
        user.id,
        { page: query.page, pageSize: query.pageSize },
        ANONYMOUS_VIEWER,
      ),
    );
  }
}

function presentPage(page: Page<VideoView>) {
  return { ...page, items: page.items.map(presentVideoSummary) };
}
