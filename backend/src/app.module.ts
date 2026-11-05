import { Module } from '@nestjs/common';
import { AuthModule } from './modules/auth/auth.module.js';
import { ChannelsModule } from './modules/channels/channels.module.js';
import { CommentsModule } from './modules/comments/comments.module.js';
import { LivesModule } from './modules/lives/lives.module.js';
import { ProductsModule } from './modules/products/products.module.js';
import { MessagingModule } from './modules/messaging/messaging.module.js';
import { PlaylistsModule } from './modules/playlists/playlists.module.js';
import { PostsModule } from './modules/posts/posts.module.js';
import { PremiumModule } from './modules/premium/premium.module.js';
import { VideosModule } from './modules/videos/videos.module.js';
import { InfrastructureModule } from './shared/infrastructure/infrastructure.module.js';

@Module({
  imports: [
    InfrastructureModule,
    AuthModule,
    VideosModule,
    ChannelsModule,
    CommentsModule,
    PremiumModule,
    PostsModule,
    PlaylistsModule,
    MessagingModule,
    LivesModule,
    ProductsModule,
  ],
})
export class AppModule {}
