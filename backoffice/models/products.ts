// Produits vendus pendant les lives (ADR-029), tels que renvoyés par GET /admin/products.
export interface Product {
  id: string;
  channel: { id: string; name: string };
  name: string;
  // En centimes.
  price: number;
  currency: string;
  // Chemin relatif /media/product-images/… ou URL complète.
  imageUrl: string;
  description: string;
  createdAt: string;
}

// Champs saisis : le prix est saisi en euros (« 49,90 »), converti en centimes à l'envoi.
export interface ProductDraft {
  channelId: string;
  name: string;
  price: string;
  description: string;
}

export const EMPTY_PRODUCT_DRAFT: ProductDraft = { channelId: "", name: "", price: "", description: "" };

// Mêmes limites que l'API.
export const PRODUCT_NAME_MAX_LENGTH = 80;
export const PRODUCT_DESCRIPTION_MAX_LENGTH = 500;
// Minimum de Stripe pour l'euro : 0,50 € (ADR-029).
export const PRODUCT_MIN_PRICE_CENTS = 50;
export const PRODUCT_MAX_PRICE_CENTS = 1_000_000;
export const MAX_PRODUCT_IMAGE_BYTES = 5 * 1024 * 1024;

/**
 * Prix saisi en euros (« 49,90 », « 49.9 », « 50 ») converti en centimes ; null si la saisie n'est
 * pas un montant à deux décimales au plus.
 */
export function eurosToCents(value: string): number | null {
  const text = value.trim().replace(",", ".");
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return null;
  return Math.round(Number(text) * 100);
}

/** Centimes en saisie d'euros, pour préremplir le champ (« 4990 » donne « 49,90 »). */
export function centsToEuros(cents: number): string {
  return (cents / 100).toFixed(2).replace(".", ",");
}

/** Message d'erreur pour [draft], ou null s'il est valide (mêmes règles que l'API). */
export function productDraftError(draft: ProductDraft, options: { needsChannel: boolean }): string | null {
  if (options.needsChannel && !draft.channelId) return "Choisissez une chaîne.";
  const name = draft.name.trim();
  if (!name) return "Saisissez un nom.";
  if (name.length > PRODUCT_NAME_MAX_LENGTH) {
    return `Le nom ne doit pas dépasser ${PRODUCT_NAME_MAX_LENGTH} caractères.`;
  }
  const cents = eurosToCents(draft.price);
  if (cents === null) return "Saisissez un prix en euros, par exemple 49,90.";
  if (cents < PRODUCT_MIN_PRICE_CENTS) return "Le prix minimum est de 0,50 € (minimum de Stripe).";
  if (cents > PRODUCT_MAX_PRICE_CENTS) return "Le prix ne doit pas dépasser 10 000 €.";
  const description = draft.description.trim();
  if (!description) return "Saisissez une description.";
  if (description.length > PRODUCT_DESCRIPTION_MAX_LENGTH) {
    return `La description ne doit pas dépasser ${PRODUCT_DESCRIPTION_MAX_LENGTH} caractères.`;
  }
  return null;
}
