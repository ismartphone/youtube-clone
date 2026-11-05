-- AlterTable
ALTER TABLE "lives" ADD COLUMN     "pinnedProductId" UUID;

-- CreateTable
CREATE TABLE "products" (
    "id" UUID NOT NULL,
    "channelId" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "price" INTEGER NOT NULL,
    "currency" TEXT NOT NULL DEFAULT 'eur',
    "imageUrl" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "products_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "live_purchases" (
    "id" UUID NOT NULL,
    "liveId" UUID NOT NULL,
    "buyerId" UUID NOT NULL,
    "productId" UUID,
    "productName" TEXT NOT NULL,
    "amount" INTEGER NOT NULL,
    "currency" TEXT NOT NULL,
    "paymentReference" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "live_purchases_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "products_channelId_createdAt_idx" ON "products"("channelId", "createdAt" DESC);

-- CreateIndex
CREATE UNIQUE INDEX "live_purchases_paymentReference_key" ON "live_purchases"("paymentReference");

-- CreateIndex
CREATE INDEX "live_purchases_liveId_createdAt_idx" ON "live_purchases"("liveId", "createdAt" DESC);

-- AddForeignKey
ALTER TABLE "lives" ADD CONSTRAINT "lives_pinnedProductId_fkey" FOREIGN KEY ("pinnedProductId") REFERENCES "products"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "products" ADD CONSTRAINT "products_channelId_fkey" FOREIGN KEY ("channelId") REFERENCES "channels"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "live_purchases" ADD CONSTRAINT "live_purchases_liveId_fkey" FOREIGN KEY ("liveId") REFERENCES "lives"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "live_purchases" ADD CONSTRAINT "live_purchases_buyerId_fkey" FOREIGN KEY ("buyerId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "live_purchases" ADD CONSTRAINT "live_purchases_productId_fkey" FOREIGN KEY ("productId") REFERENCES "products"("id") ON DELETE SET NULL ON UPDATE CASCADE;
