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
  UseGuards,
} from '@nestjs/common';
import { Transform } from 'class-transformer';
import { IsString, Length } from 'class-validator';
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
import { ListCommentsUseCase } from '../../application/list-comments.use-case.js';
import { PostCommentUseCase } from '../../application/post-comment.use-case.js';
import { COMMENT_MAX_LENGTH } from '../../domain/comment.js';
import { CommentsDisabledError } from '../../domain/comment-rules.js';
import { presentComment } from './comment.presenter.js';

export class PostCommentBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, COMMENT_MAX_LENGTH)
  content: string;
}

@Controller('videos/:videoId/comments')
export class CommentsController {
  constructor(
    private readonly listComments: ListCommentsUseCase,
    private readonly postComment: PostCommentUseCase,
  ) {}

  // Ouverte aux visiteurs. total sert de nombre de commentaires sous la vidéo ; commentsDisabled
  // indique que les commentaires sont désactivés (la liste est alors vide, ADR-016).
  @Get()
  @UseGuards(OptionalJwtAuthGuard)
  async list(
    @Param('videoId', ParseUUIDPipe) videoId: string,
    @Query() query: PaginationQuery,
    @OptionalCurrentUser() user: AuthenticatedUser | undefined,
  ) {
    const page = await this.listComments.execute(
      videoId,
      { page: query.page, pageSize: query.pageSize },
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
    let comment;
    try {
      comment = await this.postComment.execute(videoId, user.id, body.content);
    } catch (error) {
      if (error instanceof CommentsDisabledError) {
        throw new ForbiddenException(error.message);
      }
      throw error;
    }
    if (!comment) {
      throw new NotFoundException('Vidéo introuvable');
    }
    return presentComment(comment);
  }
}
