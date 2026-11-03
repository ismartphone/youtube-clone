import {
  Body,
  ConflictException,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Post,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UseGuards,
} from '@nestjs/common';
import { Transform } from 'class-transformer';
import { IsString, Length } from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import { JwtAuthGuard } from '../../../auth/interfaces/http/jwt-auth.guard.js';
import {
  CancelPremiumUseCase,
  ConfirmPurchaseUseCase,
  GetPaymentConfigUseCase,
  GetPremiumStatusUseCase,
  StartCheckoutUseCase,
} from '../../application/premium.use-cases.js';
import type { ActiveSubscription } from '../../domain/premium.js';
import {
  AlreadyPremiumError,
  InvalidPurchaseTokenError,
  NotPremiumError,
  PaymentAlreadyUsedError,
  PaymentUnavailableError,
} from '../../domain/premium.errors.js';

// Corps de POST /me/premium/subscribe : la référence reçue de POST /me/premium/checkout,
// soit l'identifiant du PaymentIntent (mode stripe), soit le jeton simulé (mode simulated).
export class SubscribeBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, 200)
  purchaseToken: string;
}

// Forme exacte des réponses : isPremium vaut false et les dates null hors abonnement.
function presentStatus(subscription: ActiveSubscription | null) {
  return {
    isPremium: subscription !== null,
    startedAt: subscription?.startedAt ?? null,
    expiresAt: subscription?.expiresAt ?? null,
  };
}

// Traduit les erreurs métier communes à checkout et subscribe en codes HTTP.
function toHttpError(error: unknown): unknown {
  if (
    error instanceof AlreadyPremiumError ||
    error instanceof PaymentAlreadyUsedError
  ) {
    return new ConflictException(error.message);
  }
  if (error instanceof InvalidPurchaseTokenError) {
    // 422 et non 400 : la requête est bien formée, c'est le paiement qui n'est pas accepté.
    return new UnprocessableEntityException(error.message);
  }
  if (error instanceof PaymentUnavailableError) {
    return new ServiceUnavailableException(error.message);
  }
  return error;
}

// Abonnement Premium de l'utilisateur connecté (PREMIUM_SUBSCRIPTION, ADR-007 et ADR-014).
@Controller('me/premium')
@UseGuards(JwtAuthGuard)
export class PremiumController {
  constructor(
    private readonly getStatus: GetPremiumStatusUseCase,
    private readonly getPaymentConfig: GetPaymentConfigUseCase,
    private readonly startCheckout: StartCheckoutUseCase,
    private readonly confirmPurchase: ConfirmPurchaseUseCase,
    private readonly cancelPremium: CancelPremiumUseCase,
  ) {}

  @Get()
  async status(@CurrentUser() user: AuthenticatedUser) {
    return presentStatus(await this.getStatus.execute(user.id));
  }

  // Mode de paiement actif : « stripe » avec la clé publiable, ou « simulated » (repli).
  @Get('config')
  config() {
    return this.getPaymentConfig.execute();
  }

  // Étape 1 : le serveur démarre le paiement, au montant qu'il fixe lui-même.
  @Post('checkout')
  async checkout(@CurrentUser() user: AuthenticatedUser) {
    try {
      const payment = await this.startCheckout.execute(user.id);
      return {
        mode: payment.mode,
        purchaseToken: payment.reference,
        clientSecret: payment.clientSecret,
        expiresAt: payment.expiresAt,
      };
    } catch (error) {
      throw toHttpError(error);
    }
  }

  // Étape 2 : le serveur vérifie le paiement puis accorde un mois de Premium.
  @Post('subscribe')
  @HttpCode(HttpStatus.OK)
  async subscribe(
    @CurrentUser() user: AuthenticatedUser,
    @Body() body: SubscribeBody,
  ) {
    try {
      return presentStatus(
        await this.confirmPurchase.execute(user.id, body.purchaseToken),
      );
    } catch (error) {
      throw toHttpError(error);
    }
  }

  // Fin immédiate de l'abonnement en cours.
  @Delete()
  async cancel(@CurrentUser() user: AuthenticatedUser) {
    try {
      await this.cancelPremium.execute(user.id);
      return presentStatus(null);
    } catch (error) {
      if (error instanceof NotPremiumError) {
        throw new NotFoundException(error.message);
      }
      throw error;
    }
  }
}
