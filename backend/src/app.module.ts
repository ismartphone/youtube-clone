import { Module } from '@nestjs/common';
import { VideosModule } from './modules/videos/videos.module.js';
import { InfrastructureModule } from './shared/infrastructure/infrastructure.module.js';

@Module({
  imports: [InfrastructureModule, VideosModule],
})
export class AppModule {}
