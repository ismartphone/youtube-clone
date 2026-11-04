import {
  Body,
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
  Put,
  Query,
  UseGuards,
} from '@nestjs/common';
import { Transform, Type } from 'class-transformer';
import {
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  Min,
} from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import {
  CurrentUser,
  OptionalCurrentUser,
} from '../../../auth/interfaces/http/current-user.decorator.js';
import {
  JwtAuthGuard,
  OptionalJwtAuthGuard,
} from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { PaginationQuery } from '../../../videos/interfaces/http/list-videos.query.js';
import {
  HeartCommentUseCase,
  ReactToCommentUseCase,
} from '../../application/comment-actions.use-case.js';
import {
  ListCommentsUseCase,
  ListRepliesUseCase,
} from '../../application/list-comments.use-case.js';
import { PostCommentUseCase } from '../../application/post-comment.use-case.js';
import {
  COMMENT_MAX_LENGTH,
  COMMENT_SORTS,
  REPLIES_PAGE_SIZE,
  type CommentReaction,
  type CommentSort,
} from '../../domain/comment.js';
import {
  CommentsDisabledError,
  NotVideoOwnerError,
} from '../../domain/comment-rules.js';
import { presentComment } from './comment.presenter.js';

export class PostCommentBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, COMMENT_MAX_LENGTH)
  content: string;

  // Réponse (ADR-023) : le commentaire auquel on répond, réponse comprise.
  @IsOptional()
  @IsUUID()
  parentId?: string;
}

export class ListCommentsQuery extends PaginationQuery {
  @IsOptional()
  @IsIn(COMMENT_SORTS)
  sort: CommentSort = 'TOP';
}

export class ListRepliesQuery {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page: number = 1;
}

// Commentaires désactivés et cœur réservé au créateur : 403 ; le reste remonte tel quel.
function forbidden(error: unknown): never {
  if (
    error instanceof CommentsDisabledError ||
    error instanceof NotVideoOwnerError
  ) {
    throw new ForbiddenException(error.message);
  }
  throw error;
}

@Controller('videos/:videoId/comments')
export class CommentsController {
  constructor(
    private readonly listComments: ListCommentsUseCase,
    private readonly postComment: PostCommentUseCase,
  ) {}

  // Ouverte aux visiteurs. Commentaires de premier niveau, triés « Top » par défaut ; total
  // compte aussi les réponses, c'est le nombre sous la vidéo. commentsDisabled indique que les
  // commentaires sont désactivés (la liste est alors vide, ADR-016).
  @Get()
  @UseGuards(OptionalJwtAuthGuard)
  async list(
    @Param('videoId', ParseUUIDPipe) videoId: string,
    @Query() query: ListCommentsQuery,
    @OptionalCurrentUser() user: AuthenticatedUser | undefined,
  ) {
    const page = await this.listComments.execute(
      videoId,
      { page: query.page, pageSize: query.pageSize, sort: query.sort },
      user?.id ?? null,
    );
    if (!page) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return { ...page, items: page.items.map(presentComment) };
  }

  @Post()
  @UseGuards(JwtAuthGuard)
  async create(
    @CurrentUser() user: AuthenticatedUser,
    @Param('videoId', ParseUUIDPipe) videoId: string,
    @Body() body: PostCommentBody,
  ) {
    const comment = await this.postComment
      .execute(videoId, user.id, body.content, body.parentId ?? null)
      .catch(forbidden);
    if (!comment) {
      throw new NotFoundException('Vidéo ou commentaire introuvable');
    }
    return presentComment(comment);
  }
}

// Actions sur un commentaire ou une réponse (ADR-023) : fil des réponses, avis, cœur du créateur.
@Controller('comments/:id')
export class CommentActionsController {
  constructor(
    private readonly listReplies: ListRepliesUseCase,
    private readonly reactToComment: ReactToCommentUseCase,
    private readonly heartComment: HeartCommentUseCase,
  ) {}

  // Ouverte aux visiteurs : 10 réponses par page, de la plus ancienne à la plus récente.
  @Get('replies')
  @UseGuards(OptionalJwtAuthGuard)
  async replies(
    @Param('id', ParseUUIDPipe) id: string,
    @Query() query: ListRepliesQuery,
    @OptionalCurrentUser() user: AuthenticatedUser | undefined,
  ) {
    const page = await this.listReplies.execute(
      id,
      { page: query.page, pageSize: REPLIES_PAGE_SIZE },
      user?.id ?? null,
    );
    if (!page) {
      throw new NotFoundException('Commentaire introuvable');
    }
    return { ...page, items: page.items.map(presentComment) };
  }

  @Post('like')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  like(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.react(user.id, id, 'LIKE', true);
  }

  @Delete('like')
  @UseGuards(JwtAuthGuard)
  unlike(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.react(user.id, id, 'LIKE', false);
  }

  // « Je n'aime pas » : retire le « j'aime » éventuel ; il n'est jamais compté.
  @Post('dislike')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  dislike(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.react(user.id, id, 'DISLIKE', true);
  }

  @Delete('dislike')
  @UseGuards(JwtAuthGuard)
  undislike(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.react(user.id, id, 'DISLIKE', false);
  }

  @Put('heart')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(JwtAuthGuard)
  heart(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.setHeart(user.id, id, true);
  }

  @Delete('heart')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(JwtAuthGuard)
  unheart(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.setHeart(user.id, id, false);
  }

  private async react(
    userId: string,
    commentId: string,
    reaction: CommentReaction,
    on: boolean,
  ) {
    const status = await this.reactToComment
      .execute(userId, commentId, reaction, on)
      .catch(forbidden);
    if (!status) {
      throw new NotFoundException('Commentaire introuvable');
    }
    return status;
  }

  private async setHeart(userId: string, commentId: string, hearted: boolean) {
    const found = await this.heartComment
      .execute(userId, commentId, hearted)
      .catch(forbidden);
    if (!found) {
      throw new NotFoundException('Commentaire introuvable');
    }
  }
}
