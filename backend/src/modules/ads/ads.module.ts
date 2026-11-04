import { Module } from '@nestjs/common';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
} from '../../shared/infrastructure/media.config.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { LocalChannelImageStore } from '../channels/infrastructure/local-channel-image-store.js';
import { FfmpegHlsGenerator } from '../videos/infrastructure/ffmpeg-hls-generator.js';
import { FfmpegThumbnailGenerator } from '../videos/infrastructure/ffmpeg-thumbnail-generator.js';
import { FfprobeVideoProber } from '../videos/infrastructure/ffprobe-video-prober.js';
import {
  ManageAdsUseCase,
  RandomAdPicker,
} from './application/ads.use-cases.js';
import { AD_PICKER } from './domain/ad-picker.js';
import {
  AD_FILE_STORE,
  AD_REPOSITORY,
  type AdFileStore,
  type AdRepository,
} from './domain/ports.js';
import { ChannelImageAdLogoStore } from './infrastructure/channel-image-ad-logo-store.js';
import { LocalAdFileStore } from './infrastructure/local-ad-file-store.js';
import { PrismaAdRepository } from './infrastructure/prisma-ad.repository.js';
import { AdminAdsController } from './interfaces/http/admin-ads.controller.js';

// Publicités vidéo (ADR-021). Les autres modules n'en voient que le port AD_PICKER : c'est le
// module videos qui sait si le spectateur est Premium et si la vidéo est un Short.
// Les outils FFmpeg sont ceux des vidéos, pour le même traitement des fichiers.
@Module({
  imports: [AuthModule],
  controllers: [AdminAdsController],
  providers: [
    {
      provide: AD_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaAdRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: AD_FILE_STORE,
      useFactory: () => new LocalAdFileStore(mediaDirectory()),
    },
    {
      provide: ManageAdsUseCase,
      useFactory: (ads: AdRepository, files: AdFileStore) => {
        const mediaDir = mediaDirectory();
        return new ManageAdsUseCase(
          ads,
          files,
          new FfprobeVideoProber(ffprobeCommand()),
          new FfmpegThumbnailGenerator({ command: ffmpegCommand(), mediaDir }),
          new FfmpegHlsGenerator({
            ffmpeg: ffmpegCommand(),
            ffprobe: ffprobeCommand(),
            mediaDir,
          }),
          new ChannelImageAdLogoStore(
            new LocalChannelImageStore({
              ffmpeg: ffmpegCommand(),
              ffprobe: ffprobeCommand(),
              mediaDir,
            }),
          ),
        );
      },
      inject: [AD_REPOSITORY, AD_FILE_STORE],
    },
    {
      provide: AD_PICKER,
      useFactory: (ads: AdRepository) => new RandomAdPicker(ads),
      inject: [AD_REPOSITORY],
    },
  ],
  exports: [AD_PICKER],
})
export class AdsModule {}
