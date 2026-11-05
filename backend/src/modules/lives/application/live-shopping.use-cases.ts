import type { LiveRooms } from '../../realtime/domain/live-rooms.js';
import { LiveEndedError, type LiveRecord } from '../domain/live.js';
import {
  InvalidLivePaymentError,
  MIN_PAYMENT_CENTS,
  NoPinnedProductError,
  OwnProductPurchaseError,
  ProductNotPinnableError,
  toProductCard,
  type AdminLive,
  type LivePaymentGateway,
  type LivePaymentStart,
  type LiveProductCard,
  type LivePurchaseRecord,
  type LiveShoppingRepository,
} from '../domain/shopping.js';
import { LivesUseCases, NotLiveOwnerError } from './lives.use-cases.js';

/**
 * Produit épinglé et achat pendant un live (ADR-029, étape 3). Le serveur est seul juge : le
 * propriétaire du live, le produit de sa chaîne, le prix lu en base, le paiement vérifié chez
 * le fournisseur et à usage unique.
 */
export class LiveShoppingUseCases {
  constructor(
    private readonly shopping: LiveShoppingRepository,
    private readonly lives: LivesUseCases,
    private readonly rooms: LiveRooms,
    private readonly payments: LivePaymentGateway,
  ) {}

  // Produits de la chaîne de [userId], pour choisir celui à épingler ; vide sans chaîne.
  async listOwnProducts(userId: string): Promise<LiveProductCard[]> {
    const channelId = await this.shopping.findOwnChannelId(userId);
    return channelId ? this.shopping.listChannelProducts(channelId) : [];
  }

  /**
   * Épingle [productId] sur le live : la carte apparaît en direct chez tous les spectateurs.
   * Remplace le produit déjà épinglé. null si le live n'existe pas. Lève NotLiveOwnerError,
   * LiveEndedError ou ProductNotPinnableError (produit inconnu ou d'une autre chaîne).
   */
  async pin(
    userId: string,
    liveId: string,
    productId: string,
  ): Promise<LiveProductCard | null> {
    const live = await this.ownedRunningLive(userId, liveId);
    if (!live) {
      return null;
    }
    const product = await this.shopping.findProduct(productId);
    if (!product || product.channelId !== live.channel.id) {
      throw new ProductNotPinnableError();
    }
    await this.shopping.setPinnedProduct(liveId, product.id);
    const card = toProductCard(product);
    this.rooms.toLive(liveId, 'live:pinned', { liveId, product: card });
    return card;
  }

  // Retire le produit épinglé : la carte disparaît chez tous. Sans effet si rien n'est épinglé.
  async unpin(userId: string, liveId: string): Promise<boolean> {
    const live = await this.ownedRunningLive(userId, liveId);
    if (!live) {
      return false;
    }
    await this.shopping.setPinnedProduct(liveId, null);
    this.rooms.toLive(liveId, 'live:pinned', { liveId, product: null });
    return true;
  }

  /**
   * Démarre le paiement du produit actuellement épinglé d'un live en cours. Le prix est lu en base,
   * jamais reçu du client. null si le live n'existe pas. Lève LiveEndedError, NoPinnedProductError
   * ou OwnProductPurchaseError (le diffuseur n'achète pas son produit).
   */
  async checkout(
    userId: string,
    liveId: string,
  ): Promise<LivePaymentStart | null> {
    const live = await this.lives.findFresh(liveId);
    if (!live) {
      return null;
    }
    if (live.status !== 'LIVE') {
      throw new LiveEndedError();
    }
    if (live.channel.ownerId === userId) {
      throw new OwnProductPurchaseError();
    }
    const pinned = live.pinnedProduct;
    // Revérifie en base : le produit a pu être supprimé ou son prix changer depuis la lecture.
    const product = pinned ? await this.shopping.findProduct(pinned.id) : null;
    if (!product || product.price < MIN_PAYMENT_CENTS) {
      throw new NoPinnedProductError();
    }
    return this.payments.start(userId, liveId, toProductCard(product));
  }

  /**
   * Vérifie le paiement [reference] puis enregistre l'achat (ADR-029, D8). Un paiement réglé est
   * enregistré même si le live s'est terminé ou si le produit a été désépinglé entre-temps : l'argent
   * est pris. L'annonce dans le chat n'est écrite que si le live est encore en cours. null si le live
   * n'existe pas ; lève InvalidLivePaymentError si le paiement n'est pas accepté et
   * LivePaymentAlreadyUsedError s'il a déjà servi.
   */
  async confirmPurchase(
    userId: string,
    liveId: string,
    reference: string,
  ): Promise<LivePurchaseRecord | null> {
    const live = await this.lives.findFresh(liveId);
    if (!live) {
      return null;
    }
    const payment = await this.payments.verify(userId, liveId, reference);
    if (!payment) {
      throw new InvalidLivePaymentError();
    }
    // Le produit a pu être supprimé depuis : l'achat garde alors son nom et son montant.
    const product = await this.shopping.findProduct(payment.productId);
    const { purchase, message } = await this.shopping.recordPurchase({
      liveId,
      buyerId: userId,
      productId: product?.id ?? null,
      productName: payment.productName,
      amount: payment.amount,
      currency: payment.currency,
      paymentReference: reference,
      announce: live.status === 'LIVE',
    });
    if (message) {
      this.rooms.toLive(liveId, 'live:message', message);
    }
    return purchase;
  }

  // Administration : tous les lives avec leurs achats. Données personnelles et financières.
  listForAdmin(): Promise<AdminLive[]> {
    return this.shopping.listForAdmin();
  }

  private async ownedRunningLive(
    userId: string,
    liveId: string,
  ): Promise<LiveRecord | null> {
    const live = await this.lives.findFresh(liveId);
    if (!live) {
      return null;
    }
    if (live.channel.ownerId !== userId) {
      throw new NotLiveOwnerError();
    }
    if (live.status !== 'LIVE') {
      throw new LiveEndedError();
    }
    return live;
  }
}
