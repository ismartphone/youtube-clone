import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
} from '@nestjs/common';
import { GetVideoUseCase } from '../../application/get-video.use-case.js';
import { ListVideosUseCase } from '../../application/list-videos.use-case.js';
import { RecordViewUseCase } from '../../application/record-view.use-case.js';
import { ANONYMOUS_VIEWER } from '../../domain/video.js';
import { ListVideosQuery } from './list-videos.query.js';
import { presentVideoDetail, presentVideoSummary } from './video.presenter.js';

// Sans authentification pour l'instant, chaque requête est faite par un spectateur anonyme.
@Controller('videos')
export class VideosController {
  constructor(
    private readonly listVideos: ListVideosUseCase,
    private readonly getVideo: GetVideoUseCase,
    private readonly recordView: RecordViewUseCase,
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

  @Get(':id')
  async detail(@Param('id', ParseUUIDPipe) id: string) {
    const video = await this.getVideo.execute(id, ANONYMOUS_VIEWER);
    if (!video) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return presentVideoDetail(video);
  }

  @Post(':id/view')
  @HttpCode(HttpStatus.NO_CONTENT)
  async view(@Param('id', ParseUUIDPipe) id: string) {
    if (!(await this.recordView.execute(id))) {
      throw new NotFoundException('Vidéo introuvable');
    }
  }
}
