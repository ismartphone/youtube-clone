import { describe, expect, it } from 'vitest';
import type { LiveRooms } from '../../realtime/domain/live-rooms.js';
import { LiveEndedError, type LiveRecord } from '../domain/live.js';
import {
  InvalidLivePaymentError,
  LivePaymentAlreadyUsedError,
  NoPinnedProductError,
  OwnProductPurchaseError,
  ProductNotPinnableError,
  purchaseAnnouncement,
  type AdminLive,
  type LivePaymentGateway,
  type LiveProduct,
  type LiveShoppingRepository,
  type NewLivePurchase,
  type VerifiedLivePayment,
} from '../domain/shopping.js';
import { LiveShoppingUseCases } from './live-shopping.use-cases.js';
import { LivesUseCases, NotLiveOwnerError } from './lives.use-cases.js';

const PRODUCT: LiveProduct = {
  id: 'casque',
  channelId: 'chaine',
  name: 'Casque audio',
  price: 4990,
  currency: 'eur',
  imageUrl: '/media/product-images/a.jpg',
  description: 'Un casque.',
};

function liveRecord(overrides: Partial<LiveRecord> = {}): LiveRecord {
  return {
    id: 'live',
    title: 'Mon live',
    status: 'LIVE',
    startedAt: new Date('2026-11-05T10:00:00Z'),
    endedAt: null,
    channel: {
      id: 'chaine',
      name: 'Studio Libre',
      handle: 'studiolibre',
      avatarUrl: null,
      ownerId: 'diffuseur',
    },
    video: {
      id: 'video',
      videoUrl: '/media/videos/a.mp4',
      hlsUrl: null,
      thumbnailUrl: '/media/thumbnails/a.jpg',
      durationSeconds: 52,
      format: 'LONG',
      channelId: 'chaine',
      visibility: 'PUBLIC',
      isPremium: false,
    },
    pinnedProduct: null,
    ...overrides,
  };
}

class FakeRooms implements LiveRooms {
  events: { liveId: string; event: string; data: unknown }[] = [];
  toLive(liveId: string, event: string, data: unknown) {
    this.events.push({ liveId, event, data });
  }
  viewerCount() {
    return 0;
  }
  close() {}
}

class FakeLives {
  live: LiveRecord | null = liveRecord();
  findFresh() {
    return Promise.resolve(this.live);
  }
}

class FakeShopping implements LiveShoppingRepository {
  products = new Map<string, LiveProduct>([[PRODUCT.id, PRODUCT]]);
  pinned: string | null | undefined;
  purchases: NewLivePurchase[] = [];
  usedReferences = new Set<string>();

  findOwnChannelId(userId: string) {
    return Promise.resolve(userId === 'diffuseur' ? 'chaine' : null);
  }
  findProduct(id: string) {
    return Promise.resolve(this.products.get(id) ?? null);
  }
  listChannelProducts() {
    return Promise.resolve([...this.products.values()]);
  }
  setPinnedProduct(_liveId: string, productId: string | null) {
    this.pinned = productId;
    return Promise.resolve();
  }
  recordPurchase(purchase: NewLivePurchase) {
    if (this.usedReferences.has(purchase.paymentReference)) {
      return Promise.reject(new LivePaymentAlreadyUsedError());
    }
    this.usedReferences.add(purchase.paymentReference);
    this.purchases.push(purchase);
    return Promise.resolve({
      purchase: {
        id: 'achat',
        liveId: purchase.liveId,
        productName: purchase.productName,
        createdAt: new Date(),
      },
      message: purchase.announce
        ? {
            id: 'annonce',
            liveId: purchase.liveId,
            kind: 'PURCHASE' as const,
            content: purchaseAnnouncement(
              'Camille Spectatrice',
              purchase.productName,
            ),
            createdAt: new Date(),
            author: {
              id: purchase.buyerId,
              displayName: 'Camille Spectatrice',
              avatarUrl: null,
            },
          }
        : null,
    });
  }
  listForAdmin(): Promise<AdminLive[]> {
    return Promise.resolve([]);
  }
}

class FakePayments implements LivePaymentGateway {
  started: { buyerId: string; price: number }[] = [];
  verified: VerifiedLivePayment | null = {
    productId: 'casque',
    productName: 'Casque audio',
    amount: 4990,
    currency: 'eur',
  };
  start(buyerId: string, _liveId: string, product: { price: number }) {
    this.started.push({ buyerId, price: product.price });
    return Promise.resolve({
      mode: 'simulated' as const,
      reference: 'jeton',
      clientSecret: null,
      publishableKey: null,
      expiresAt: null,
    });
  }
  verify() {
    return Promise.resolve(this.verified);
  }
}

function setup() {
  const lives = new FakeLives();
  const shopping = new FakeShopping();
  const rooms = new FakeRooms();
  const payments = new FakePayments();
  const useCases = new LiveShoppingUseCases(
    shopping,
    lives as unknown as LivesUseCases,
    rooms,
    payments,
  );
  return { lives, shopping, rooms, payments, useCases };
}

describe('purchaseAnnouncement', () => {
  it('prend le premier mot du nom affiché, sans le montant', () => {
    expect(purchaseAnnouncement('Camille Spectatrice', 'Casque audio')).toBe(
      "Camille vient d'acheter Casque audio",
    );
  });

  it('garde un nom d’un seul mot tel quel', () => {
    expect(purchaseAnnouncement('Camille', 'Casque audio')).toBe(
      "Camille vient d'acheter Casque audio",
    );
    expect(purchaseAnnouncement('  Camille  ', 'Casque')).toBe(
      "Camille vient d'acheter Casque",
    );
  });
});

describe('LiveShoppingUseCases.pin', () => {
  it('épingle un produit de la chaîne et le diffuse à la salle', async () => {
    const { useCases, shopping, rooms } = setup();
    const card = await useCases.pin('diffuseur', 'live', 'casque');
    expect(card).toMatchObject({ id: 'casque', name: 'Casque audio' });
    expect(card).not.toHaveProperty('channelId');
    expect(shopping.pinned).toBe('casque');
    expect(rooms.events).toEqual([
      {
        liveId: 'live',
        event: 'live:pinned',
        data: { liveId: 'live', product: card },
      },
    ]);
  });

  it("refuse un produit d'une autre chaîne ou inconnu", async () => {
    const { useCases, shopping } = setup();
    shopping.products.set('autre', {
      ...PRODUCT,
      id: 'autre',
      channelId: 'ailleurs',
    });
    await expect(
      useCases.pin('diffuseur', 'live', 'autre'),
    ).rejects.toBeInstanceOf(ProductNotPinnableError);
    await expect(
      useCases.pin('diffuseur', 'live', 'inconnu'),
    ).rejects.toBeInstanceOf(ProductNotPinnableError);
  });

  it('refuse un autre compte que le diffuseur et un live terminé', async () => {
    const { useCases, lives } = setup();
    await expect(
      useCases.pin('spectateur', 'live', 'casque'),
    ).rejects.toBeInstanceOf(NotLiveOwnerError);
    lives.live = liveRecord({ status: 'ENDED' });
    await expect(
      useCases.pin('diffuseur', 'live', 'casque'),
    ).rejects.toBeInstanceOf(LiveEndedError);
  });

  it('renvoie null pour un live inconnu', async () => {
    const { useCases, lives } = setup();
    lives.live = null;
    expect(await useCases.pin('diffuseur', 'live', 'casque')).toBeNull();
  });

  it('retire le produit épinglé et le diffuse', async () => {
    const { useCases, shopping, rooms } = setup();
    expect(await useCases.unpin('diffuseur', 'live')).toBe(true);
    expect(shopping.pinned).toBeNull();
    expect(rooms.events[0]).toEqual({
      liveId: 'live',
      event: 'live:pinned',
      data: { liveId: 'live', product: null },
    });
  });
});

describe('LiveShoppingUseCases.checkout', () => {
  const pinnedLive = () =>
    liveRecord({
      pinnedProduct: {
        id: PRODUCT.id,
        name: PRODUCT.name,
        price: PRODUCT.price,
        currency: PRODUCT.currency,
        imageUrl: PRODUCT.imageUrl,
        description: PRODUCT.description,
      },
    });

  it('démarre le paiement au prix lu en base, jamais reçu du client', async () => {
    const { useCases, lives, shopping, payments } = setup();
    lives.live = pinnedLive();
    // Prix changé en base depuis l'épinglage : c'est celui de la base qui compte.
    shopping.products.set('casque', { ...PRODUCT, price: 5990 });
    await useCases.checkout('spectateur', 'live');
    expect(payments.started).toEqual([{ buyerId: 'spectateur', price: 5990 }]);
  });

  it("refuse l'achat du diffuseur, sans produit épinglé et sur un live terminé", async () => {
    const { useCases, lives } = setup();
    lives.live = pinnedLive();
    await expect(useCases.checkout('diffuseur', 'live')).rejects.toBeInstanceOf(
      OwnProductPurchaseError,
    );
    lives.live = liveRecord();
    await expect(
      useCases.checkout('spectateur', 'live'),
    ).rejects.toBeInstanceOf(NoPinnedProductError);
    lives.live = { ...pinnedLive(), status: 'ENDED' };
    await expect(
      useCases.checkout('spectateur', 'live'),
    ).rejects.toBeInstanceOf(LiveEndedError);
  });

  it('refuse si le produit épinglé a été supprimé depuis', async () => {
    const { useCases, lives, shopping } = setup();
    lives.live = pinnedLive();
    shopping.products.clear();
    await expect(
      useCases.checkout('spectateur', 'live'),
    ).rejects.toBeInstanceOf(NoPinnedProductError);
  });
});

describe('LiveShoppingUseCases.confirmPurchase', () => {
  it("enregistre l'achat et annonce « Prénom vient d'acheter … » à la salle, sans montant", async () => {
    const { useCases, shopping, rooms } = setup();
    const purchase = await useCases.confirmPurchase(
      'spectateur',
      'live',
      'jeton',
    );
    expect(purchase).toMatchObject({ productName: 'Casque audio' });
    expect(shopping.purchases[0]).toMatchObject({
      buyerId: 'spectateur',
      productId: 'casque',
      amount: 4990,
      currency: 'eur',
      paymentReference: 'jeton',
      announce: true,
    });
    const announce = rooms.events.find((e) => e.event === 'live:message');
    expect(JSON.stringify(announce?.data)).toContain(
      "Camille vient d'acheter Casque audio",
    );
    expect(JSON.stringify(announce?.data)).not.toMatch(
      /4990|49,90|"amount"|"currency"/,
    );
  });

  it('refuse un paiement non accepté, sans rien enregistrer', async () => {
    const { useCases, shopping, payments } = setup();
    payments.verified = null;
    await expect(
      useCases.confirmPurchase('spectateur', 'live', 'jeton'),
    ).rejects.toBeInstanceOf(InvalidLivePaymentError);
    expect(shopping.purchases).toHaveLength(0);
  });

  it('un même paiement ne sert qu’une fois', async () => {
    const { useCases } = setup();
    await useCases.confirmPurchase('spectateur', 'live', 'jeton');
    await expect(
      useCases.confirmPurchase('spectateur', 'live', 'jeton'),
    ).rejects.toBeInstanceOf(LivePaymentAlreadyUsedError);
  });

  it('enregistre un paiement réglé après la fin du live, sans annonce', async () => {
    const { useCases, lives, shopping, rooms } = setup();
    lives.live = liveRecord({ status: 'ENDED' });
    await useCases.confirmPurchase('spectateur', 'live', 'jeton');
    expect(shopping.purchases[0]).toMatchObject({ announce: false });
    expect(rooms.events).toHaveLength(0);
  });

  it("garde l'achat si le produit a été supprimé entre-temps", async () => {
    const { useCases, shopping } = setup();
    shopping.products.clear();
    await useCases.confirmPurchase('spectateur', 'live', 'jeton');
    expect(shopping.purchases[0]).toMatchObject({
      productId: null,
      productName: 'Casque audio',
    });
  });

  it('renvoie null pour un live inconnu', async () => {
    const { useCases, lives } = setup();
    lives.live = null;
    expect(
      await useCases.confirmPurchase('spectateur', 'live', 'jeton'),
    ).toBeNull();
  });
});
