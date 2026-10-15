import { Module } from '@nestjs/common';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import {
  VIDEO_ROOM_PUBLISHER,
  type VideoRoomPublisher,
} from '../realtime/domain/video-room-publisher.js';
import { RealtimeModule } from '../realtime/realtime.module.js';
import { ListCommentsUseCase } from './application/list-comments.use-case.js';
import { PostCommentUseCase } from './application/post-comment.use-case.js';
import { COMMENT_REPOSITORY, type CommentRepository } from './domain/ports.js';
import { PrismaCommentRepository } from './infrastructure/prisma-comment.repository.js';
import { CommentsController } from './interfaces/http/comments.controller.js';

@Module({
  imports: [AuthModule, RealtimeModule],
  controllers: [CommentsController],
  providers: [
    {
      provide: COMMENT_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaCommentRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: ListCommentsUseCase,
      useFactory: (comments: CommentRepository) =>
        new ListCommentsUseCase(comments),
      inject: [COMMENT_REPOSITORY],
    },
    {
      provide: PostCommentUseCase,
      useFactory: (
        comments: CommentRepository,
        publisher: VideoRoomPublisher,
      ) => new PostCommentUseCase(comments, publisher),
      inject: [COMMENT_REPOSITORY, VIDEO_ROOM_PUBLISHER],
    },
  ],
})
export class CommentsModule {}
