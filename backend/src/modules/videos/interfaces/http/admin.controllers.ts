import {
  BadRequestException,
  Body,
  ConflictException,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UseGuards,
} from '@nestjs/common';
import { AdminGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { CreateVideoUseCase } from '../../application/create-video.use-case.js';
import { DeleteVideoUseCase } from '../../application/delete-video.use-case.js';
import { GetCatalogStatsUseCase } from '../../application/get-catalog-stats.use-case.js';
import { GetVideoUseCase } from '../../application/get-video.use-case.js';
import { ListVideosUseCase } from '../../application/list-videos.use-case.js';
import { RegenerateThumbnailUseCase } from '../../application/regenerate-thumbnail.use-case.js';
import {
  CreateCategoryUseCase,
  DeleteCategoryUseCase,
  ListCategoriesUseCase,
} from '../../application/manage-categories.use-case.js';
import { UpdateVideoUseCase } from '../../application/update-video.use-case.js';
import {
  CategoryAlreadyExistsError,
  UnknownCategoryError,
  UnknownChannelError,
} from '../../domain/catalog.errors.js';
import {
  ThumbnailGenerationError,
  ThumbnailUnavailableError,
} from '../../domain/thumbnail-generator.js';
import { ADMIN_VIEWER } from '../../domain/video.js';
import {
  CreateCategoryBody,
  CreateVideoBody,
  UpdateVideoBody,
} from './admin.bodies.js';
import { PaginationQuery } from './list-videos.query.js';
import { presentVideoDetail, presentVideoSummary } from './video.presenter.js';

// Routes du back-office (ADR-005) : réservées au rôle ADMIN.

// Référence inconnue dans le corps de la requête : erreur de saisie, pas ressource absente.
// Miniature : FFmpeg absent (503, le service est indisponible) ou image impossible à extraire
// de cette vidéo (422, l'URL ou le fichier pose problème).
function translateCatalogError(error: unknown): never {
  if (
    error instanceof UnknownChannelError ||
    error instanceof UnknownCategoryError
  ) {
    throw new BadRequestException(error.message);
  }
  if (error instanceof ThumbnailUnavailableError) {
    throw new ServiceUnavailableException(error.message);
  }
  if (error instanceof ThumbnailGenerationError) {
    throw new UnprocessableEntityException(error.message);
  }
  throw error;
}

@Controller('admin/videos')
@UseGuards(AdminGuard)
export class AdminVideosController {
  constructor(
    private readonly listVideos: ListVideosUseCase,
    private readonly getVideo: GetVideoUseCase,
    private readonly createVideo: CreateVideoUseCase,
    private readonly updateVideo: UpdateVideoUseCase,
    private readonly deleteVideo: DeleteVideoUseCase,
    private readonly regenerateThumbnail: RegenerateThumbnailUseCase,
  ) {}

  // Toutes les vidéos, Shorts compris, de la plus récente à la plus ancienne.
  @Get()
  async list(@Query() query: PaginationQuery) {
    const page = await this.listVideos.execute(
      {},
      { page: query.page, pageSize: query.pageSize },
      ADMIN_VIEWER,
    );
    return { ...page, items: page.items.map(presentVideoSummary) };
  }

  @Get(':id')
  async detail(@Param('id', ParseUUIDPipe) id: string) {
    const video = await this.getVideo.execute(id, ADMIN_VIEWER);
    if (!video) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return presentVideoDetail(video);
  }

  @Post()
  async create(@Body() body: CreateVideoBody) {
    try {
      return presentVideoDetail(await this.createVideo.execute(body));
    } catch (error) {
      translateCatalogError(error);
    }
  }

  @Patch(':id')
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: UpdateVideoBody,
  ) {
    let video;
    try {
      video = await this.updateVideo.execute(id, body);
    } catch (error) {
      translateCatalogError(error);
    }
    if (!video) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return presentVideoDetail(video);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(@Param('id', ParseUUIDPipe) id: string) {
    if (!(await this.deleteVideo.execute(id))) {
      throw new NotFoundException('Vidéo introuvable');
    }
  }

  // Bouton « Régénérer la miniature » : nouvelle image extraite à 10 % de la durée.
  @Post(':id/thumbnail')
  @HttpCode(HttpStatus.OK)
  async regenerate(@Param('id', ParseUUIDPipe) id: string) {
    let thumbnailUrl: string | null;
    try {
      thumbnailUrl = await this.regenerateThumbnail.execute(id);
    } catch (error) {
      translateCatalogError(error);
    }
    const video = thumbnailUrl && (await this.getVideo.execute(id, ADMIN_VIEWER));
    if (!video) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return presentVideoDetail(video);
  }
}

@Controller('admin/categories')
@UseGuards(AdminGuard)
export class AdminCategoriesController {
  constructor(
    private readonly listCategories: ListCategoriesUseCase,
    private readonly createCategory: CreateCategoryUseCase,
    private readonly deleteCategory: DeleteCategoryUseCase,
  ) {}

  @Get()
  async list() {
    const categories = await this.listCategories.execute();
    return categories.map((category) => ({
      id: category.id,
      name: category.name,
      videoCount: category.videoCount,
    }));
  }

  @Post()
  async create(@Body() body: CreateCategoryBody) {
    try {
      const category = await this.createCategory.execute(body.name);
      return { id: category.id, name: category.name };
    } catch (error) {
      if (error instanceof CategoryAlreadyExistsError) {
        throw new ConflictException(error.message);
      }
      throw error;
    }
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(@Param('id', ParseUUIDPipe) id: string) {
    if (!(await this.deleteCategory.execute(id))) {
      throw new NotFoundException('Catégorie introuvable');
    }
  }
}

@Controller('admin/stats')
@UseGuards(AdminGuard)
export class AdminStatsController {
  constructor(private readonly getStats: GetCatalogStatsUseCase) {}

  @Get()
  async stats() {
    const stats = await this.getStats.execute();
    return {
      users: stats.users,
      channels: stats.channels,
      videos: stats.videos,
      totalViews: stats.totalViews,
    };
  }
}
