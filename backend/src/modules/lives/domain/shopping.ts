// Produit épinglé et achat pendant un live (ADR-029, étape 3), indépendants de Prisma et de NestJS.

import type { LiveChatMessage } from './live.js';

// Prix minimum d'un paiement en euro chez Stripe : 0,50 € (ADR-029). Vérifié aussi à l'ajout d'un
// produit ; revérifié ici car le prix est lu en base au moment de payer.
export const MIN_PAYMENT_CENTS = 50;

// Carte du produit épinglé, telle que la voient les spectateurs : jamais plus que ceci.
export interface LiveProductCard {
  id: string;
  name: string;
  // En centimes.
  price: number;
  currency: string;
  // Chemin relatif /media/product-images/… (ADR-011).
  imageUrl: string;
  description: string;
}

// Produit vu par le live : avec sa chaîne, pour vérifier qu'il appartient au diffuseur.
export interface LiveProduct extends LiveProductCard {
  channelId: string;
}

export function toProductCard(product: LiveProductCard): LiveProductCard {
  return {
    id: product.id,
    name: product.name,
    price: product.price,
    currency: product.currency,
    imageUrl: product.imageUrl,
    description: product.description,
  };
}

/**
 * Texte de l'annonce d'un achat dans le chat (ADR-029, D9) : le prénom est le premier mot du nom
 * affiché ; un nom d'un seul mot donne ce mot. Jamais le montant ni le moyen de paiement.
 */
export function purchaseAnnouncement(
  displayName: string,
  productName: string,
): string {
  const firstName = displayName.trim().split(/\s+/)[0] || displayName.trim();
  return `${firstName} vient d'acheter ${productName}`;
}

// Paiement démarré pour un produit : [reference] est renvoyée pour confirmer.
export interface LivePaymentStart {
  mode: 'stripe' | 'simulated';
  reference: string;
  clientSecret: string | null;
  // Clé publiable Stripe pour la PaymentSheet ; null en mode simulé.
  publishableKey: string | null;
  expiresAt: Date | null;
}

// Ce qu'un paiement vérifié a réellement réglé : le produit et le montant fixés au démarrage,
// recopiés dans l'achat (ADR-029).
export interface VerifiedLivePayment {
  productId: string;
  productName: string;
  // Montant encaissé, en centimes, et devise ISO en minuscules.
  amount: number;
  currency: string;
}

export const LIVE_PAYMENT_GATEWAY = Symbol('LIVE_PAYMENT_GATEWAY');

// Fournisseur de paiement propre au live (ADR-029, D8) : mêmes clés Stripe et même repli simulé
// que le Premium (ADR-014), sans toucher au module premium. Le montant vient toujours du serveur.
export interface LivePaymentGateway {
  // Démarre le paiement de [product] par [buyerId] pour le live [liveId]. Lève
  // LivePaymentUnavailableError si le fournisseur ne répond pas.
  start(
    buyerId: string,
    liveId: string,
    product: LiveProductCard,
  ): Promise<LivePaymentStart>;
  // Vérifie que le paiement [reference] est réglé, pour cet acheteur et ce live ; null sinon. Lève
  // LivePaymentUnavailableError si le fournisseur ne répond pas.
  verify(
    buyerId: string,
    liveId: string,
    reference: string,
  ): Promise<VerifiedLivePayment | null>;
}

export const LIVE_SHOPPING_REPOSITORY = Symbol('LIVE_SHOPPING_REPOSITORY');

export interface NewLivePurchase {
  liveId: string;
  buyerId: string;
  // Vide si le produit a été supprimé depuis le démarrage du paiement.
  productId: string | null;
  productName: string;
  amount: number;
  currency: string;
  paymentReference: string;
  // Vrai si le live est encore en cours : l'annonce est alors écrite dans le chat.
  announce: boolean;
}

export interface LivePurchaseRecord {
  id: string;
  liveId: string;
  productName: string;
  createdAt: Date;
}

// Achat tel que le voit l'administration : données personnelles et financières (ADR-014).
export interface AdminPurchase {
  id: string;
  buyer: { id: string; displayName: string; email: string };
  productName: string;
  amount: number;
  currency: string;
  paymentReference: string;
  createdAt: Date;
}

export interface AdminLive {
  id: string;
  title: string;
  status: 'LIVE' | 'ENDED';
  startedAt: Date;
  endedAt: Date | null;
  channel: { id: string; name: string };
  purchases: AdminPurchase[];
}

export interface LiveShoppingRepository {
  // Chaîne dont [userId] est propriétaire ; null s'il n'en a pas.
  findOwnChannelId(userId: string): Promise<string | null>;
  findProduct(productId: string): Promise<LiveProduct | null>;
  // Produits de la chaîne, du plus récent au plus ancien : ce que le diffuseur peut épingler.
  listChannelProducts(channelId: string): Promise<LiveProductCard[]>;
  setPinnedProduct(liveId: string, productId: string | null): Promise<void>;
  /**
   * Enregistre l'achat et, si [announce], l'annonce dans le chat, en une transaction. Lève
   * LivePaymentAlreadyUsedError si la référence a déjà servi (colonne unique). [message] vaut
   * null sans annonce.
   */
  recordPurchase(purchase: NewLivePurchase): Promise<{
    purchase: LivePurchaseRecord;
    message: LiveChatMessage | null;
  }>;
  // Tous les lives, du plus récent au plus ancien, avec leurs achats.
  listForAdmin(): Promise<AdminLive[]>;
}

export class ProductNotPinnableError extends Error {
  constructor() {
    super('Épinglez un produit de votre chaîne.');
  }
}

export class NoPinnedProductError extends Error {
  constructor() {
    super("Aucun produit n'est épinglé pour le moment.");
  }
}

export class OwnProductPurchaseError extends Error {
  constructor() {
    super('Vous ne pouvez pas acheter votre propre produit.');
  }
}

export class InvalidLivePaymentError extends Error {
  constructor() {
    super("Le paiement n'a pas été accepté.");
  }
}

export class LivePaymentAlreadyUsedError extends Error {
  constructor() {
    super('Ce paiement a déjà été utilisé.');
  }
}

export class LivePaymentUnavailableError extends Error {
  constructor() {
    super('Le paiement est indisponible, réessayez dans un instant.');
  }
}
