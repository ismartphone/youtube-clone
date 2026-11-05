import {
  BadRequestException,
  Body,
  ConflictException,
  Controller,
  Delete,
  ForbiddenException,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
  Put,
  ServiceUnavailableException,
  UnprocessableEntityException,
  UseGuards,
} from '@nestjs/common';
import { Transform } from 'class-transformer';
import { IsString, IsUUID, Length } from 'class-validator';
import type { AuthenticatedUser } from '../../../auth/domain/user.js';
import { CurrentUser } from '../../../auth/interfaces/http/current-user.decorator.js';
import {
  AdminGuard,
  JwtAuthGuard,
} from '../../../auth/interfaces/http/jwt-auth.guard.js';
import { LiveShoppingUseCases } from '../../application/live-shopping.use-cases.js';
import {
  LivesUseCases,
  NotLiveOwnerError,
} from '../../application/lives.use-cases.js';
import { LiveEndedError } from '../../domain/live.js';
import {
  InvalidLivePaymentError,
  LivePaymentAlreadyUsedError,
  LivePaymentUnavailableError,
  NoPinnedProductError,
  OwnProductPurchaseError,
  ProductNotPinnableError,
} from '../../domain/shopping.js';

export class PinProductBody {
  @IsUUID()
  productId: string;
}

// Référence reçue de POST /lives/:id/checkout : identifiant du PaymentIntent (mode stripe) ou jeton
// simulé (mode simulated), comme pour le Premium (ADR-014).
export class LivePurchaseBody {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, 200)
  purchaseToken: string;
}

// Erreurs du domaine traduites en réponses HTTP : le message est affiché tel quel par l'application.
function translate(error: unknown): never {
  if (
    error instanceof NotLiveOwnerError ||
    error instanceof OwnProductPurchaseError
  ) {
    throw new ForbiddenException(error.message);
  }
  if (error instanceof ProductNotPinnableError) {
    throw new BadRequestException(error.message);
  }
  if (
    error instanceof LiveEndedError ||
    error instanceof NoPinnedProductError ||
    error instanceof LivePaymentAlreadyUsedError
  ) {
    throw new ConflictException(error.message);
  }
  if (error instanceof InvalidLivePaymentError) {
    // 422 et non 400 : la requête est bien formée, c'est le paiement qui n'est pas accepté.
    throw new UnprocessableEntityException(error.message);
  }
  if (error instanceof LivePaymentUnavailableError) {
    throw new ServiceUnavailableException(error.message);
  }
  throw error;
}

function found<T>(value: T | null | false): T {
  if (value === null || value === false) {
    throw new NotFoundException('Live introuvable');
  }
  return value;
}

// Produits de la chaîne du diffuseur, pour choisir celui à épingler (ADR-029).
@Controller('me/products')
@UseGuards(JwtAuthGuard)
export class MyProductsController {
  constructor(private readonly shopping: LiveShoppingUseCases) {}

  @Get()
  async list(@CurrentUser() user: AuthenticatedUser) {
    return { items: await this.shopping.listOwnProducts(user.id) };
  }
}

// Produit épinglé et achat pendant un live (ADR-029, étape 3).
@Controller('lives')
@UseGuards(JwtAuthGuard)
export class LiveShoppingController {
  constructor(private readonly shopping: LiveShoppingUseCases) {}

  @Put(':id/pinned-product')
  async pin(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: PinProductBody,
  ) {
    try {
      return found(await this.shopping.pin(user.id, id, body.productId));
    } catch (error) {
      translate(error);
    }
  }

  @Delete(':id/pinned-product')
  @HttpCode(HttpStatus.NO_CONTENT)
  async unpin(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    try {
      found(await this.shopping.unpin(user.id, id));
    } catch (error) {
      translate(error);
    }
  }

  // Étape 1 de l'achat : le serveur démarre le paiement du produit épinglé, au prix qu'il lit.
  @Post(':id/checkout')
  async checkout(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    try {
      const payment = found(await this.shopping.checkout(user.id, id));
      return {
        mode: payment.mode,
        purchaseToken: payment.reference,
        clientSecret: payment.clientSecret,
        publishableKey: payment.publishableKey,
        expiresAt: payment.expiresAt,
      };
    } catch (error) {
      translate(error);
    }
  }

  // Étape 2 : le serveur vérifie le paiement puis enregistre l'achat et l'annonce dans le chat.
  @Post(':id/purchases')
  @HttpCode(HttpStatus.OK)
  async purchase(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() body: LivePurchaseBody,
  ) {
    try {
      const purchase = found(
        await this.shopping.confirmPurchase(user.id, id, body.purchaseToken),
      );
      // Jamais le montant : l'application n'en a pas besoin, elle l'a déjà affiché.
      return {
        id: purchase.id,
        productName: purchase.productName,
        createdAt: purchase.createdAt,
      };
    } catch (error) {
      translate(error);
    }
  }
}

// Lives du back-office (ADR-029) : liste avec leurs achats et fin d'un live resté ouvert (D2).
// Données personnelles et financières, réservées aux administrateurs, comme les paiements Premium.
@Controller('admin/lives')
@UseGuards(AdminGuard)
export class AdminLivesController {
  constructor(
    private readonly shopping: LiveShoppingUseCases,
    private readonly lives: LivesUseCases,
  ) {}

  @Get()
  async list() {
    return { items: await this.shopping.listForAdmin() };
  }

  @Post(':id/end')
  @HttpCode(HttpStatus.OK)
  async end(@Param('id', ParseUUIDPipe) id: string) {
    return found(await this.lives.endByAdmin(id));
  }
}
