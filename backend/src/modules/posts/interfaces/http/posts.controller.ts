import {
  Body,
  Controller,
  ForbiddenException,
  Get,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UploadedFile,
  UseFilters,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { Transform, Type } from 'class-transformer';
import { IsInt, IsOptional, IsString, Length, Max, Min } from 'class-validator';
import { tmpdir } from 'node:os';
import {
  FileTooLargeFilter,
  RemoveTempFileInterceptor,
  type ReceivedFile,
} from '../../../../shared/interfaces/http/file-upload.js';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import {
  CreatePostUseCase,
  GetLatestPostUseCase,
  ListChannelPostsUseCase,
  ListFollowedPostsUseCase,
} from '../../application/post.use-cases.js';
import {
  ChannelNotFoundError,
  ImageUnavailableError,
  InvalidImageError,
  NoChannelError,
} from '../../domain/post.errors.js';
import {
  MAX_POST_IMAGE_BYTES,
  POST_TEXT_MAX_LENGTH,
  type Post as ChannelPost,
} from '../../domain/post.js';

// Champ texte de l'envoi multipart, à côté de l'image facultative (champ « image »).
export class CreatePostBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, POST_TEXT_MAX_LENGTH, {
    message: `Le texte doit faire entre 1 et ${POST_TEXT_MAX_LENGTH} caractères.`,
  })
  text: string;
}

export class ListPostsQuery {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  pageSize: number = 20;
}

function presentPost(post: ChannelPost) {
  return {
    id: post.id,
    text: post.text,
    imageUrl: post.imageUrl,
    createdAt: post.createdAt,
    channel: post.channel,
  };
}

// Publication d'un post (ADR-015) : réservée au propriétaire d'une chaîne. Le garde
// d'authentification passe avant la réception : un anonyme n'écrit rien sur le disque.
@Controller('me/posts')
@UseGuards(JwtAuthGuard)
export class MyPostsController {
  constructor(private readonly createPost: CreatePostUseCase) {}

  @Post()
  @UseFilters(
    new FileTooLargeFilter(
      `L'image dépasse la taille maximale de ${MAX_POST_IMAGE_BYTES / 1024 / 1024} Mo.`,
    ),
  )
  @UseInterceptors(
    FileInterceptor('image', {
      // Nom aléatoire sans extension, généré par multer : celui du client n'est jamais repris.
      dest: tmpdir(),
      limits: { fileSize: MAX_POST_IMAGE_BYTES, files: 1 },
    }),
    RemoveTempFileInterceptor,
  )
  async create(
    @CurrentUser() user: AuthenticatedUser,
    @UploadedFile() image: ReceivedFile | undefined,
    @Body() body: CreatePostBody,
  ) {
    try {
      return presentPost(
        await this.createPost.execute(user.id, body.text, image?.path ?? null),
      );
    } catch (error) {
      if (error instanceof NoChannelError) {
        throw new ForbiddenException(error.message);
      }
      if (error instanceof InvalidImageError) {
        throw new UnprocessableEntityException(error.message);
      }
      if (error instanceof ImageUnavailableError) {
        throw new ServiceUnavailableException(error.message);
      }
      throw error;
    }
  }
}

// Onglet Posts de la page de chaîne : lecture publique, du plus récent au plus ancien.
@Controller('channels/:id/posts')
export class ChannelPostsController {
  constructor(private readonly listPosts: ListChannelPostsUseCase) {}

  @Get()
  async list(
    @Param('id', ParseUUIDPipe) channelId: string,
    @Query() query: ListPostsQuery,
  ) {
    try {
      const page = await this.listPosts.execute(
        channelId,
        query.page,
        query.pageSize,
      );
      return { ...page, items: page.items.map(presentPost) };
    } catch (error) {
      if (error instanceof ChannelNotFoundError) {
        throw new NotFoundException(error.message);
      }
      throw error;
    }
  }
}

// Post mis en avant dans le fil d'accueil : le plus récent de la plateforme, ou null.
@Controller('posts')
export class LatestPostController {
  constructor(private readonly latestPost: GetLatestPostUseCase) {}

  @Get('latest')
  async latest() {
    const post = await this.latestPost.execute();
    return { post: post ? presentPost(post) : null };
  }
}

// Posts des chaînes suivies, pour le fil Abonnements.
@Controller('me/feed/posts')
@UseGuards(JwtAuthGuard)
export class FollowedPostsController {
  constructor(private readonly listPosts: ListFollowedPostsUseCase) {}

  @Get()
  async list(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: ListPostsQuery,
  ) {
    const page = await this.listPosts.execute(
      user.id,
      query.page,
      query.pageSize,
    );
    return { ...page, items: page.items.map(presentPost) };
  }
}
