import { Logger, Module } from '@nestjs/common';
import type { Redis } from 'ioredis';
import { requireEnv } from '../../shared/infrastructure/env.js';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import { LIVE_ROOMS, type LiveRooms } from '../realtime/domain/live-rooms.js';
import { RealtimeModule } from '../realtime/realtime.module.js';
import { LiveShoppingUseCases } from './application/live-shopping.use-cases.js';
import { LivesUseCases } from './application/lives.use-cases.js';
import { LIVES_REPOSITORY, type LivesRepository } from './domain/ports.js';
import {
  LIVE_PAYMENT_GATEWAY,
  LIVE_SHOPPING_REPOSITORY,
  type LivePaymentGateway,
  type LiveShoppingRepository,
} from './domain/shopping.js';
import { PrismaLiveShoppingRepository } from './infrastructure/prisma-live-shopping.repository.js';
import { PrismaLivesRepository } from './infrastructure/prisma-lives.repository.js';
import { SimulatedLivePaymentGateway } from './infrastructure/simulated-live-payment-gateway.js';
import { StripeLivePaymentGateway } from './infrastructure/stripe-live-payment-gateway.js';
import {
  AdminLivesController,
  LiveShoppingController,
  MyProductsController,
} from './interfaces/http/live-shopping.controller.js';
import {
  LivesController,
  MyLivesController,
} from './interfaces/http/lives.controller.js';

// Même choix que le Premium (ADR-014) : Stripe en mode test si STRIPE_SECRET_KEY est définie, sinon
// l'achat simulé de repli. Le module premium n'est pas touché (ADR-029, D8).
function createLivePaymentGateway(redis: Redis): LivePaymentGateway {
  const secretKey = process.env.STRIPE_SECRET_KEY;
  const logger = new Logger('LivesModule');
  if (!secretKey) {
    logger.log('STRIPE_SECRET_KEY absente : achat de live simulé (repli)');
    return new SimulatedLivePaymentGateway(redis);
  }
  logger.log('Achat de live par Stripe en mode test');
  return new StripeLivePaymentGateway(
    secretKey,
    requireEnv('STRIPE_PUBLISHABLE_KEY'),
  );
}

// Live shopping (ADR-029) : direct simulé sur une vidéo du catalogue, avec son chat et son compteur
// de spectateurs, diffusés par la passerelle temps réel existante.
@Module({
  imports: [AuthModule, RealtimeModule],
  controllers: [
    MyLivesController,
    LivesController,
    MyProductsController,
    LiveShoppingController,
    AdminLivesController,
  ],
  providers: [
    {
      provide: LIVES_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaLivesRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: LivesUseCases,
      useFactory: (repository: LivesRepository, rooms: LiveRooms) =>
        new LivesUseCases(repository, rooms),
      inject: [LIVES_REPOSITORY, LIVE_ROOMS],
    },
    {
      provide: LIVE_SHOPPING_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaLiveShoppingRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: LIVE_PAYMENT_GATEWAY,
      useFactory: createLivePaymentGateway,
      inject: [REDIS_CLIENT],
    },
    {
      provide: LiveShoppingUseCases,
      useFactory: (
        shopping: LiveShoppingRepository,
        lives: LivesUseCases,
        rooms: LiveRooms,
        payments: LivePaymentGateway,
      ) => new LiveShoppingUseCases(shopping, lives, rooms, payments),
      inject: [
        LIVE_SHOPPING_REPOSITORY,
        LivesUseCases,
        LIVE_ROOMS,
        LIVE_PAYMENT_GATEWAY,
      ],
    },
  ],
})
export class LivesModule {}
