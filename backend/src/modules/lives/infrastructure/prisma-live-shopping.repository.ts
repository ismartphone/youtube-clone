import type { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { purchaseAnnouncement } from '../domain/shopping.js';
import {
  LivePaymentAlreadyUsedError,
  type AdminLive,
  type LiveProduct,
  type LiveProductCard,
  type LivePurchaseRecord,
  type LiveShoppingRepository,
  type NewLivePurchase,
} from '../domain/shopping.js';
import type { LiveChatMessage } from '../domain/live.js';

const PRODUCT_SELECT = {
  id: true,
  channelId: true,
  name: true,
  price: true,
  currency: true,
  imageUrl: true,
  description: true,
} satisfies Prisma.ProductSelect;

const MESSAGE_SELECT = {
  id: true,
  liveId: true,
  kind: true,
  content: true,
  createdAt: true,
  user: { select: { id: true, displayName: true, avatarUrl: true } },
} satisfies Prisma.LiveMessageSelect;

// Code Prisma « violation de contrainte d'unicité ».
const UNIQUE_VIOLATION = 'P2002';

export class PrismaLiveShoppingRepository implements LiveShoppingRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findOwnChannelId(userId: string): Promise<string | null> {
    const channel = await this.prisma.channel.findUnique({
      where: { ownerId: userId },
      select: { id: true },
    });
    return channel?.id ?? null;
  }

  findProduct(productId: string): Promise<LiveProduct | null> {
    return this.prisma.product.findUnique({
      where: { id: productId },
      select: PRODUCT_SELECT,
    });
  }

  async listChannelProducts(channelId: string): Promise<LiveProductCard[]> {
    const rows = await this.prisma.product.findMany({
      where: { channelId },
      orderBy: { createdAt: 'desc' },
      select: PRODUCT_SELECT,
    });
    return rows.map(({ channelId: _channelId, ...card }) => card);
  }

  async setPinnedProduct(
    liveId: string,
    productId: string | null,
  ): Promise<void> {
    await this.prisma.live.update({
      where: { id: liveId },
      data: { pinnedProductId: productId },
    });
  }

  async recordPurchase(purchase: NewLivePurchase): Promise<{
    purchase: LivePurchaseRecord;
    message: LiveChatMessage | null;
  }> {
    try {
      return await this.prisma.$transaction(async (tx) => {
        const created = await tx.livePurchase.create({
          data: {
            liveId: purchase.liveId,
            buyerId: purchase.buyerId,
            productId: purchase.productId,
            productName: purchase.productName,
            amount: purchase.amount,
            currency: purchase.currency,
            paymentReference: purchase.paymentReference,
          },
          select: {
            id: true,
            liveId: true,
            productName: true,
            createdAt: true,
          },
        });
        if (!purchase.announce) {
          return { purchase: created, message: null };
        }
        const buyer = await tx.user.findUniqueOrThrow({
          where: { id: purchase.buyerId },
          select: { displayName: true },
        });
        // Annonce écrite par le serveur : jamais le montant ni le moyen de paiement (ADR-029, D9).
        const row = await tx.liveMessage.create({
          data: {
            liveId: purchase.liveId,
            userId: purchase.buyerId,
            kind: 'PURCHASE',
            content: purchaseAnnouncement(
              buyer.displayName,
              purchase.productName,
            ),
          },
          select: MESSAGE_SELECT,
        });
        return {
          purchase: created,
          message: {
            id: row.id,
            liveId: row.liveId,
            kind: row.kind,
            content: row.content,
            createdAt: row.createdAt,
            author: row.user,
          },
        };
      });
    } catch (error) {
      if ((error as { code?: string }).code === UNIQUE_VIOLATION) {
        throw new LivePaymentAlreadyUsedError();
      }
      throw error;
    }
  }

  async listForAdmin(): Promise<AdminLive[]> {
    const lives = await this.prisma.live.findMany({
      orderBy: { startedAt: 'desc' },
      select: {
        id: true,
        title: true,
        status: true,
        startedAt: true,
        endedAt: true,
        channel: { select: { id: true, name: true } },
        purchases: {
          orderBy: { createdAt: 'desc' },
          select: {
            id: true,
            productName: true,
            amount: true,
            currency: true,
            paymentReference: true,
            createdAt: true,
            buyer: { select: { id: true, displayName: true, email: true } },
          },
        },
      },
    });
    return lives;
  }
}
