import type { ChannelImageStore } from '../../channels/domain/ports.js';
import type { LiveRooms } from '../../realtime/domain/live-rooms.js';
import type { ProductsRepository } from '../domain/ports.js';
import {
  ProductChannelNotFoundError,
  ProductNotFoundError,
  type NewProduct,
  type Product,
  type ProductChanges,
} from '../domain/product.js';

/**
 * Gestion des produits par l'administration (ADR-029, D11). L'image est validée par ffprobe
 * d'après son contenu et recadrée, comme la photo de chaîne (ADR-011, ADR-018).
 */
export class ManageProductsUseCase {
  constructor(
    private readonly products: ProductsRepository,
    private readonly images: ChannelImageStore,
    private readonly rooms: LiveRooms,
  ) {}

  list(): Promise<Product[]> {
    return this.products.findAll();
  }

  // [imagePath] : fichier reçu, rangé sous un nom généré. Lève InvalidChannelImageError si ce
  // n'est pas une image JPEG ou PNG, ProductChannelNotFoundError si la chaîne n'existe pas.
  async create(
    imagePath: string,
    product: Omit<NewProduct, 'imageUrl'>,
  ): Promise<Product> {
    if (!(await this.products.channelExists(product.channelId))) {
      throw new ProductChannelNotFoundError();
    }
    const imageUrl = await this.images.save(imagePath, 'product');
    try {
      return await this.products.create({ ...product, imageUrl });
    } catch (error) {
      await this.images.remove(imageUrl);
      throw error;
    }
  }

  async update(id: string, changes: ProductChanges): Promise<Product> {
    const product = await this.products.update(id, changes);
    if (!product) {
      throw new ProductNotFoundError();
    }
    return product;
  }

  async setImage(id: string, imagePath: string): Promise<Product> {
    if (!(await this.products.find(id))) {
      throw new ProductNotFoundError();
    }
    const imageUrl = await this.images.save(imagePath, 'product');
    const previous = await this.products.setImageUrl(id, imageUrl);
    if (!previous) {
      await this.images.remove(imageUrl);
      throw new ProductNotFoundError();
    }
    await this.images.remove(previous.imageUrl);
    return { ...previous, imageUrl };
  }

  // Un produit épinglé est retiré de ses lives : la carte disparaît chez les spectateurs.
  async remove(id: string): Promise<void> {
    const liveIds = await this.products.findRunningLiveIdsPinning(id);
    const product = await this.products.delete(id);
    if (!product) {
      throw new ProductNotFoundError();
    }
    for (const liveId of liveIds) {
      this.rooms.toLive(liveId, 'live:pinned', { liveId, product: null });
    }
    await this.images.remove(product.imageUrl);
  }
}
