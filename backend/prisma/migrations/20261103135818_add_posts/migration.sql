-- CreateTable
CREATE TABLE "posts" (
    "id" UUID NOT NULL,
    "channelId" UUID NOT NULL,
    "text" TEXT NOT NULL,
    "imageUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "posts_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "posts_channelId_createdAt_idx" ON "posts"("channelId", "createdAt" DESC);

-- AddForeignKey
ALTER TABLE "posts" ADD CONSTRAINT "posts_channelId_fkey" FOREIGN KEY ("channelId") REFERENCES "channels"("id") ON DELETE CASCADE ON UPDATE CASCADE;
