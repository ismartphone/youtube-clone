import {
  Controller,
  Delete,
  ForbiddenException,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import {
  CurrentUser,
  OptionalCurrentUser,
} from '../../../auth/interfaces/http/current-user.decorator.js';
import {
  JwtAuthGuard,
  OptionalJwtAuthGuard,
} from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { GetVideoUseCase } from '../../application/get-video.use-case.js';
import {
  GetLikeStatusUseCase,
  LikeVideoUseCase,
} from '../../application/like-video.use-case.js';
import { ListVideosUseCase } from '../../application/list-videos.use-case.js';
import { RecordViewUseCase } from '../../application/record-view.use-case.js';
import { ResolveViewerUseCase } from '../../application/resolve-viewer.use-case.js';
import { ANONYMOUS_VIEWER } from '../../domain/video.js';
import { ListVideosQuery } from './list-videos.query.js';
import { presentVideoDetail, presentVideoSummary } from './video.presenter.js';

// Code d'erreur renvoyé quand une vidéo exclusive est demandée sans abonnement Premium en cours.
export const PREMIUM_REQUIRED = 'PremiumRequired';

// Les listes ne contiennent jamais l'URL des fichiers : seul le détail d'une vidéo dépend
// du statut Premium du spectateur.
@Controller('videos')
export class VideosController {
  constructor(
    private readonly listVideos: ListVideosUseCase,
    private readonly getVideo: GetVideoUseCase,
    private readonly recordView: RecordViewUseCase,
    private readonly likeVideo: LikeVideoUseCase,
    private readonly getLikeStatus: GetLikeStatusUseCase,
    private readonly resolveViewer: ResolveViewerUseCase,
  ) {}

  @Get()
  async list(@Query() query: ListVideosQuery) {
    const page = await this.listVideos.execute(
      { format: query.format, categoryId: query.categoryId },
      { page: query.page, pageSize: query.pageSize },
      ANONYMOUS_VIEWER,
    );
    return { ...page, items: page.items.map(presentVideoSummary) };
  }

  // Ouverte aux visiteurs ; isLiked n'est vrai que pour un utilisateur connecté qui a aimé.
  @Get(':id')
  @UseGuards(OptionalJwtAuthGuard)
  async detail(
    @Param('id', ParseUUIDPipe) id: string,
    @OptionalCurrentUser() user: AuthenticatedUser | undefined,
  ) {
    const viewer = await this.resolveViewer.execute(user?.id);
    const [video, isLiked] = await Promise.all([
      this.getVideo.execute(id, viewer),
      this.getLikeStatus.execute(id, user?.id),
    ]);
    if (!video) {
      throw new NotFoundException('Vidéo introuvable');
    }
    // Vidéo exclusive sans abonnement en cours : une erreur claire, jamais l'URL (ADR-007).
    // error permet au client de distinguer ce refus d'un autre 403 et d'ouvrir le paywall.
    if (video.isPremium && !viewer.isPremium) {
      throw new ForbiddenException({
        statusCode: HttpStatus.FORBIDDEN,
        error: PREMIUM_REQUIRED,
        message: 'Cette vidéo est réservée aux membres Premium',
      });
    }
    return presentVideoDetail(video, isLiked);
  }

  // Ouverte aux visiteurs. Avec une session, la vidéo entre aussi dans l'historique de l'utilisateur.
  @Post(':id/view')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(OptionalJwtAuthGuard)
  async view(
    @Param('id', ParseUUIDPipe) id: string,
    @OptionalCurrentUser() user: AuthenticatedUser | undefined,
  ) {
    if (!(await this.recordView.execute(id, user?.id))) {
      throw new NotFoundException('Vidéo introuvable');
    }
  }

  @Post(':id/like')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  like(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.setLike(user.id, id, true);
  }

  @Delete(':id/like')
  @UseGuards(JwtAuthGuard)
  unlike(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.setLike(user.id, id, false);
  }

  private async setLike(userId: string, videoId: string, like: boolean) {
    const status = await this.likeVideo.execute(userId, videoId, like);
    if (!status) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return { likeCount: status.likeCount, isLiked: status.isLiked };
  }
}
