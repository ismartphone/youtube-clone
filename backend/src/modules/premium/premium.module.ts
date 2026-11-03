import { Module } from '@nestjs/common';
import type { Redis } from 'ioredis';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import {
  CancelPremiumUseCase,
  ConfirmPurchaseUseCase,
  GetPremiumStatusUseCase,
  StartCheckoutUseCase,
} from './application/premium.use-cases.js';
import {
  CHECKOUT_STORE,
  PREMIUM_REPOSITORY,
  type CheckoutStore,
  type PremiumRepository,
} from './domain/ports.js';
import { PrismaPremiumRepository } from './infrastructure/prisma-premium.repository.js';
import { RedisCheckoutStore } from './infrastructure/redis-checkout-store.js';
import { PremiumController } from './interfaces/http/premium.controller.js';

// Offre Premium (PREMIUM_SUBSCRIPTION, abonnement payant), à ne pas confondre avec FOLLOW.
// L'accès aux vidéos exclusives est vérifié par le module videos (ADR-007).
@Module({
  imports: [AuthModule],
  controllers: [PremiumController],
  providers: [
    {
      provide: PREMIUM_REPOSITORY,
      useFactory: (prisma: PrismaService) => new PrismaPremiumRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: CHECKOUT_STORE,
      useFactory: (redis: Redis) => new RedisCheckoutStore(redis),
      inject: [REDIS_CLIENT],
    },
    ...[GetPremiumStatusUseCase, CancelPremiumUseCase].map((UseCase) => ({
      provide: UseCase,
      useFactory: (subscriptions: PremiumRepository) => new UseCase(subscriptions),
      inject: [PREMIUM_REPOSITORY],
    })),
    ...[StartCheckoutUseCase, ConfirmPurchaseUseCase].map((UseCase) => ({
      provide: UseCase,
      useFactory: (subscriptions: PremiumRepository, checkouts: CheckoutStore) =>
        new UseCase(subscriptions, checkouts),
      inject: [PREMIUM_REPOSITORY, CHECKOUT_STORE],
    })),
  ],
})
export class PremiumModule {}
