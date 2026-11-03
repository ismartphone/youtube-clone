import { Logger, Module } from '@nestjs/common';
import type { Redis } from 'ioredis';
import { requireEnv } from '../../shared/infrastructure/env.js';
import { REDIS_CLIENT } from '../../shared/infrastructure/infrastructure.module.js';
import { PrismaService } from '../../shared/infrastructure/prisma.service.js';
import { AuthModule } from '../auth/auth.module.js';
import {
  CancelPremiumUseCase,
  ConfirmPurchaseUseCase,
  GetPaymentConfigUseCase,
  GetPremiumStatusUseCase,
  StartCheckoutUseCase,
} from './application/premium.use-cases.js';
import {
  PAYMENT_GATEWAY,
  PREMIUM_REPOSITORY,
  type PaymentGateway,
  type PremiumRepository,
} from './domain/ports.js';
import { PrismaPremiumRepository } from './infrastructure/prisma-premium.repository.js';
import { SimulatedPaymentGateway } from './infrastructure/simulated-payment-gateway.js';
import { StripePaymentGateway } from './infrastructure/stripe-payment-gateway.js';
import { PremiumController } from './interfaces/http/premium.controller.js';

// Stripe en mode test si STRIPE_SECRET_KEY est définie, sinon l'achat simulé de repli
// (ADR-014). Avec la clé secrète, la clé publiable est obligatoire : l'application en a besoin.
function createPaymentGateway(redis: Redis): PaymentGateway {
  const secretKey = process.env.STRIPE_SECRET_KEY;
  const logger = new Logger('PremiumModule');
  if (!secretKey) {
    logger.log('STRIPE_SECRET_KEY absente : achat simulé (repli)');
    return new SimulatedPaymentGateway(redis);
  }
  logger.log('Paiement par Stripe en mode test');
  return new StripePaymentGateway(
    secretKey,
    requireEnv('STRIPE_PUBLISHABLE_KEY'),
  );
}

// Offre Premium (PREMIUM_SUBSCRIPTION, abonnement payant), à ne pas confondre avec FOLLOW.
// L'accès aux vidéos exclusives est vérifié par le module videos (ADR-007).
@Module({
  imports: [AuthModule],
  controllers: [PremiumController],
  providers: [
    {
      provide: PREMIUM_REPOSITORY,
      useFactory: (prisma: PrismaService) =>
        new PrismaPremiumRepository(prisma),
      inject: [PrismaService],
    },
    {
      provide: PAYMENT_GATEWAY,
      useFactory: createPaymentGateway,
      inject: [REDIS_CLIENT],
    },
    ...[GetPremiumStatusUseCase, CancelPremiumUseCase].map((UseCase) => ({
      provide: UseCase,
      useFactory: (subscriptions: PremiumRepository) =>
        new UseCase(subscriptions),
      inject: [PREMIUM_REPOSITORY],
    })),
    {
      provide: GetPaymentConfigUseCase,
      useFactory: (payments: PaymentGateway) =>
        new GetPaymentConfigUseCase(payments),
      inject: [PAYMENT_GATEWAY],
    },
    ...[StartCheckoutUseCase, ConfirmPurchaseUseCase].map((UseCase) => ({
      provide: UseCase,
      useFactory: (
        subscriptions: PremiumRepository,
        payments: PaymentGateway,
      ) => new UseCase(subscriptions, payments),
      inject: [PREMIUM_REPOSITORY, PAYMENT_GATEWAY],
    })),
  ],
})
export class PremiumModule {}
