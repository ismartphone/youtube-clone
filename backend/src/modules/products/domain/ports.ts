import type { NewProduct, Product, ProductChanges } from './product.js';

export const PRODUCTS_REPOSITORY = Symbol('PRODUCTS_REPOSITORY');

export interface ProductsRepository {
  // Du plus récent au plus ancien.
  findAll(): Promise<Product[]>;
  find(id: string): Promise<Product | null>;
  channelExists(channelId: string): Promise<boolean>;
  create(product: NewProduct): Promise<Product>;
  // Renvoie le produit modifié, ou null s'il n'existe pas.
  update(id: string, changes: ProductChanges): Promise<Product | null>;
  // Renvoie le produit avant modification, ou null s'il n'existe pas.
  setImageUrl(id: string, imageUrl: string): Promise<Product | null>;
  // Lives en cours où le produit est épinglé : leurs spectateurs verront la carte disparaître.
  findRunningLiveIdsPinning(productId: string): Promise<string[]>;
  // Renvoie le produit supprimé, ou null s'il n'existait pas. Les achats déjà faits sont conservés.
  delete(id: string): Promise<Product | null>;
}
