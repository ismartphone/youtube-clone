import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import type { ProductsRepository } from '../domain/ports.js';
import type { NewProduct, Product, ProductChanges } from '../domain/product.js';

const SELECT = {
  id: true,
  name: true,
  price: true,
  currency: true,
  imageUrl: true,
  description: true,
  createdAt: true,
  channel: { select: { id: true, name: true } },
} satisfies Prisma.ProductSelect;

export class PrismaProductsRepository implements ProductsRepository {
  constructor(private readonly prisma: PrismaService) {}

  findAll(): Promise<Product[]> {
    return this.prisma.product.findMany({
      orderBy: { createdAt: 'desc' },
      select: SELECT,
    });
  }

  find(id: string): Promise<Product | null> {
    return this.prisma.product.findUnique({ where: { id }, select: SELECT });
  }

  async channelExists(channelId: string): Promise<boolean> {
    return (await this.prisma.channel.count({ where: { id: channelId } })) > 0;
  }

  create(product: NewProduct): Promise<Product> {
    return this.prisma.product.create({ data: product, select: SELECT });
  }

  async update(id: string, changes: ProductChanges): Promise<Product | null> {
    try {
      return await this.prisma.product.update({
        where: { id },
        data: changes,
        select: SELECT,
      });
    } catch (error) {
      if (isNotFound(error)) return null;
      throw error;
    }
  }

  async setImageUrl(id: string, imageUrl: string): Promise<Product | null> {
    const previous = await this.find(id);
    if (!previous) return null;
    const { count } = await this.prisma.product.updateMany({
      where: { id },
      data: { imageUrl },
    });
    return count > 0 ? previous : null;
  }

  async findRunningLiveIdsPinning(productId: string): Promise<string[]> {
    const lives = await this.prisma.live.findMany({
      where: { pinnedProductId: productId, status: 'LIVE' },
      select: { id: true },
    });
    return lives.map((live) => live.id);
  }

  async delete(id: string): Promise<Product | null> {
    try {
      return await this.prisma.product.delete({
        where: { id },
        select: SELECT,
      });
    } catch (error) {
      if (isNotFound(error)) return null;
      throw error;
    }
  }
}

// Code Prisma « enregistrement introuvable » (mise à jour ou suppression).
function isNotFound(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    (error as { code?: string }).code === 'P2025'
  );
}
