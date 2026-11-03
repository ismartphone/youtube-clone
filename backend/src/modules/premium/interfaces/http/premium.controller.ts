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
  GetPremiumStatusUseCase,
  StartCheckoutUseCase,
} from '../../application/premium.use-cases.js';
import type { ActiveSubscription } from '../../domain/premium.js';
import {
  AlreadyPremiumError,
  InvalidPurchaseTokenError,
  NotPremiumError,
} from '../../domain/premium.errors.js';

// Corps de POST /me/premium/subscribe : le jeton reçu de POST /me/premium/checkout.
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

// Abonnement Premium de l'utilisateur connecté (PREMIUM_SUBSCRIPTION, ADR-007 et ADR-012).
@Controller('me/premium')
@UseGuards(JwtAuthGuard)
export class PremiumController {
  constructor(
    private readonly getStatus: GetPremiumStatusUseCase,
    private readonly startCheckout: StartCheckoutUseCase,
    private readonly confirmPurchase: ConfirmPurchaseUseCase,
    private readonly cancelPremium: CancelPremiumUseCase,
  ) {}

  @Get()
  async status(@CurrentUser() user: AuthenticatedUser) {
    return presentStatus(await this.getStatus.execute(user.id));
  }

  // Étape 1 : le serveur émet le jeton d'achat, que l'application renverra pour confirmer.
  @Post('checkout')
  async checkout(@CurrentUser() user: AuthenticatedUser) {
    try {
      const checkout = await this.startCheckout.execute(user.id);
      return { purchaseToken: checkout.purchaseToken, expiresAt: checkout.expiresAt };
    } catch (error) {
      if (error instanceof AlreadyPremiumError) {
        throw new ConflictException(error.message);
      }
      throw error;
    }
  }

  // Étape 2 : le serveur vérifie le jeton puis crée l'abonnement pour un mois.
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
      if (error instanceof AlreadyPremiumError) {
        throw new ConflictException(error.message);
      }
      if (error instanceof InvalidPurchaseTokenError) {
        // 422 et non 400 : la requête est bien formée, c'est le jeton qui n'est pas accepté.
        throw new UnprocessableEntityException(error.message);
      }
      throw error;
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
