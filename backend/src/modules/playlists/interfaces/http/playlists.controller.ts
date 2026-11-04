import {
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
  UseGuards,
} from '@nestjs/common';
import { Transform } from 'class-transformer';
import { IsOptional, IsString, IsUUID, Length } from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { presentVideoSummary } from '../../../videos/interfaces/http/video.presenter.js';
import { ManagePlaylistsUseCase } from '../../application/playlist.use-cases.js';
import {
  PlaylistFullError,
  PlaylistNotFoundError,
  TooManyPlaylistsError,
  VideoNotFoundError,
} from '../../domain/playlist.errors.js';
import {
  PLAYLIST_TITLE_MAX_LENGTH,
  type PlaylistDetail,
  type PlaylistSummary,
} from '../../domain/playlist.js';

// Corps de POST et PATCH /me/playlists : le titre, sans espaces autour.
export class PlaylistTitleBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, PLAYLIST_TITLE_MAX_LENGTH, {
    message: `Le titre doit faire entre 1 et ${PLAYLIST_TITLE_MAX_LENGTH} caractères.`,
  })
  title: string;
}

export class AddVideoBody {
  @IsUUID('all', { message: 'Vidéo introuvable' })
  videoId: string;
}

export class ListPlaylistsQuery {
  // Feuille « Enregistrer » : chaque playlist indique si elle contient déjà cette vidéo.
  @IsOptional()
  @IsUUID()
  videoId?: string;
}

function presentSummary(playlist: PlaylistSummary) {
  return {
    id: playlist.id,
    title: playlist.title,
    videoCount: playlist.videoCount,
    thumbnailUrl: playlist.thumbnailUrl,
    updatedAt: playlist.updatedAt,
    ...(playlist.containsVideo === undefined
      ? {}
      : { containsVideo: playlist.containsVideo }),
  };
}

function presentDetail(playlist: PlaylistDetail) {
  return {
    id: playlist.id,
    title: playlist.title,
    videoCount: playlist.videos.length,
    updatedAt: playlist.updatedAt,
    videos: playlist.videos.map(presentVideoSummary),
  };
}

/**
 * Playlists de l'utilisateur connecté (ADR-019, partie 3). Privées : aucune route publique, et la
 * playlist d'un autre répond 404 comme si elle n'existait pas.
 */
@Controller('me/playlists')
@UseGuards(JwtAuthGuard)
export class PlaylistsController {
  constructor(private readonly playlists: ManagePlaylistsUseCase) {}

  @Get()
  async list(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: ListPlaylistsQuery,
  ) {
    return (await this.playlists.list(user.id, query.videoId)).map(
      presentSummary,
    );
  }

  @Post()
  create(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: PlaylistTitleBody,
  ) {
    return this.run(async () =>
      presentSummary(await this.playlists.create(user.id, body.title)),
    );
  }

  @Get(':id')
  detail(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.run(async () =>
      presentDetail(await this.playlists.detail(id, user.id)),
    );
  }

  @Patch(':id')
  rename(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: PlaylistTitleBody,
  ) {
    return this.run(async () =>
      presentDetail(await this.playlists.rename(id, user.id, body.title)),
    );
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  delete(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.run(() => this.playlists.delete(id, user.id));
  }

  @Post(':id/videos')
  @HttpCode(HttpStatus.NO_CONTENT)
  addVideo(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: AddVideoBody,
  ) {
    return this.run(() => this.playlists.addVideo(id, user.id, body.videoId));
  }

  @Delete(':id/videos/:videoId')
  @HttpCode(HttpStatus.NO_CONTENT)
  removeVideo(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Param('videoId', ParseUUIDPipe) videoId: string,
  ) {
    return this.run(() => this.playlists.removeVideo(id, user.id, videoId));
  }

  private async run<T>(action: () => Promise<T>): Promise<T> {
    try {
      return await action();
    } catch (error) {
      if (
        error instanceof PlaylistNotFoundError ||
        error instanceof VideoNotFoundError
      ) {
        throw new NotFoundException(error.message);
      }
      if (
        error instanceof TooManyPlaylistsError ||
        error instanceof PlaylistFullError
      ) {
        throw new ConflictException(error.message);
      }
      throw error;
    }
  }
}
