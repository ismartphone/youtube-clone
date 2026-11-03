import { Module } from '@nestjs/common';
import {
  ffprobeCommand,
  mediaDirectory,
} from '../../shared/infrastructure/media.config.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import {
  CreatePostUseCase,
  ListChannelPostsUseCase,
} from './application/post.use-cases.js';
import {
  POST_CHANNELS,
  POST_IMAGE_PROBER,
  POST_IMAGE_STORE,
  POST_REPOSITORY,
  type ImageProber,
  type ImageStore,
  type PostChannels,
  type PostRepository,
} from './domain/ports.js';
import { FfprobeImageProber } from './infrastructure/ffprobe-image-prober.js';
import { LocalPostImageStore } from './infrastructure/local-post-image-store.js';
import {
  PrismaPostChannels,
  PrismaPostRepository,
} from './infrastructure/prisma-post.repository.js';
import {
  ChannelPostsController,
  MyPostsController,
} from './interfaces/http/posts.controller.js';

// Posts des chaînes (ADR-015) : texte et image facultative. Module séparé de channels.
@Module({
  imports: [AuthModule],
  controllers: [MyPostsController, ChannelPostsController],
  providers: [
    {
      provide: POST_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaPostRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: POST_CHANNELS,
      useFactory: (prisma: PrismaService) => new PrismaPostChannels(prisma),
      inject: [PrismaService],
    },
    {
      // ffprobe est cherché au démarrage ; absent, seul l'envoi d'images est refusé.
      provide: POST_IMAGE_PROBER,
      useFactory: () => new FfprobeImageProber(ffprobeCommand()),
    },
    {
      provide: POST_IMAGE_STORE,
      useFactory: () => new LocalPostImageStore(mediaDirectory()),
    },
    {
      provide: CreatePostUseCase,
      useFactory: (
        posts: PostRepository,
        channels: PostChannels,
        prober: ImageProber,
        images: ImageStore,
      ) => new CreatePostUseCase(posts, channels, prober, images),
      inject: [
        POST_REPOSITORY,
        POST_CHANNELS,
        POST_IMAGE_PROBER,
        POST_IMAGE_STORE,
      ],
    },
    {
      provide: ListChannelPostsUseCase,
      useFactory: (posts: PostRepository, channels: PostChannels) =>
        new ListChannelPostsUseCase(posts, channels),
      inject: [POST_REPOSITORY, POST_CHANNELS],
    },
  ],
})
export class PostsModule {}
