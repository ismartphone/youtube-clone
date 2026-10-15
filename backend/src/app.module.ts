import { Module } from '@nestjs/common';
import { AuthModule } from './modules/auth/auth.module.js';
import { VideosModule } from './modules/videos/videos.module.js';
import { InfrastructureModule } from './shared/infrastructure/infrastructure.module.js';

@Module({
  imports: [InfrastructureModule, AuthModule, VideosModule],
})
export class AppModule {}
