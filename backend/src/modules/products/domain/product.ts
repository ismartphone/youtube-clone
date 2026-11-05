// Produits vendus pendant les lives (ADR-029), gérés par le back-office (D11).

export const PRODUCT_NAME_MAX_LENGTH = 80;
export const PRODUCT_DESCRIPTION_MAX_LENGTH = 500;
// Minimum de Stripe pour l'euro : 0,50 € (source dans l'ADR-029). Plafond arbitraire de 10 000 €
// pour refuser une faute de frappe.
export const PRODUCT_MIN_PRICE_CENTS = 50;
export const PRODUCT_MAX_PRICE_CENTS = 1_000_000;
export const PRODUCT_CURRENCY = 'eur';
export const PRODUCT_IMAGE_MAX_BYTES = 5 * 1024 * 1024;

// Produit tel que le voit l'administration.
export interface Product {
  id: string;
  channel: { id: string; name: string };
  name: string;
  // En centimes.
  price: number;
  currency: string;
  // Chemin relatif /media/product-images/… (ADR-011).
  imageUrl: string;
  description: string;
  createdAt: Date;
}

export interface NewProduct {
  channelId: string;
  name: string;
  price: number;
  description: string;
  imageUrl: string;
}

// Un champ absent n'est pas modifié.
export interface ProductChanges {
  name?: string;
  price?: number;
  description?: string;
}

export class ProductNotFoundError extends Error {
  constructor() {
    super('Produit introuvable');
  }
}

export class ProductChannelNotFoundError extends Error {
  constructor() {
    super('Chaîne introuvable');
  }
}
