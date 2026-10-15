import {
  Controller,
  Delete,
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
import { ANONYMOUS_VIEWER } from '../../domain/video.js';
import { ListVideosQuery } from './list-videos.query.js';
import { presentVideoDetail, presentVideoSummary } from './video.presenter.js';

// Le statut Premium n'est pas encore géré : chaque spectateur est traité comme non Premium.
@Controller('videos')
export class VideosController {
  constructor(
    private readonly listVideos: ListVideosUseCase,
    private readonly getVideo: GetVideoUseCase,
    private readonly recordView: RecordViewUseCase,
    private readonly likeVideo: LikeVideoUseCase,
    private readonly getLikeStatus: GetLikeStatusUseCase,
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
    const [video, isLiked] = await Promise.all([
      this.getVideo.execute(id, ANONYMOUS_VIEWER),
      this.getLikeStatus.execute(id, user?.id),
    ]);
    if (!video) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return presentVideoDetail(video, isLiked);
  }

  @Post(':id/view')
  @HttpCode(HttpStatus.NO_CONTENT)
  async view(@Param('id', ParseUUIDPipe) id: string) {
    if (!(await this.recordView.execute(id))) {
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
