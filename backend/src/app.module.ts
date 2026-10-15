import { Module } from '@nestjs/common';
import { AuthModule } from './modules/auth/auth.module.js';
import { ChannelsModule } from './modules/channels/channels.module.js';
import { VideosModule } from './modules/videos/videos.module.js';
import { InfrastructureModule } from './shared/infrastructure/infrastructure.module.js';

@Module({
  imports: [InfrastructureModule, AuthModule, VideosModule, ChannelsModule],
})
export class AppModule {}
