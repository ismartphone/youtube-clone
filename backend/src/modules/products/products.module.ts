import { Module } from '@nestjs/common';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
} from '../../shared/infrastructure/media.config.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { LocalChannelImageStore } from '../channels/infrastructure/local-channel-image-store.js';
import { LIVE_ROOMS, type LiveRooms } from '../realtime/domain/live-rooms.js';
import { RealtimeModule } from '../realtime/realtime.module.js';
import { ManageProductsUseCase } from './application/products.use-cases.js';
import {
  PRODUCTS_REPOSITORY,
  type ProductsRepository,
} from './domain/ports.js';
import { PrismaProductsRepository } from './infrastructure/prisma-products.repository.js';
import { AdminProductsController } from './interfaces/http/admin-products.controller.js';

// Produits vendus pendant les lives (ADR-029), gérés par le back-office seulement (D11).
// L'image est traitée comme la photo de chaîne : vérifiée par ffprobe, recadrée par FFmpeg.
@Module({
  imports: [AuthModule, RealtimeModule],
  controllers: [AdminProductsController],
  providers: [
    {
      provide: PRODUCTS_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaProductsRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: ManageProductsUseCase,
      useFactory: (products: ProductsRepository, rooms: LiveRooms) =>
        new ManageProductsUseCase(
          products,
          new LocalChannelImageStore({
            ffmpeg: ffmpegCommand(),
            ffprobe: ffprobeCommand(),
            mediaDir: mediaDirectory(),
          }),
          rooms,
        ),
      inject: [PRODUCTS_REPOSITORY, LIVE_ROOMS],
    },
  ],
})
export class ProductsModule {}
