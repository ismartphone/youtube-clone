-- CreateEnum
CREATE TYPE "LiveStatus" AS ENUM ('LIVE', 'ENDED');

-- CreateEnum
CREATE TYPE "LiveMessageKind" AS ENUM ('CHAT', 'PURCHASE');

-- CreateTable
CREATE TABLE "lives" (
    "id" UUID NOT NULL,
    "channelId" UUID NOT NULL,
    "title" TEXT NOT NULL,
    "videoId" UUID,
    "status" "LiveStatus" NOT NULL DEFAULT 'LIVE',
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "endedAt" TIMESTAMP(3),

    CONSTRAINT "lives_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "live_messages" (
    "id" UUID NOT NULL,
    "liveId" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "kind" "LiveMessageKind" NOT NULL DEFAULT 'CHAT',
    "content" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "live_messages_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "lives_status_startedAt_idx" ON "lives"("status", "startedAt" DESC);

-- CreateIndex
CREATE INDEX "lives_channelId_status_idx" ON "lives"("channelId", "status");

-- CreateIndex
CREATE INDEX "live_messages_liveId_createdAt_idx" ON "live_messages"("liveId", "createdAt" DESC);

-- AddForeignKey
ALTER TABLE "lives" ADD CONSTRAINT "lives_channelId_fkey" FOREIGN KEY ("channelId") REFERENCES "channels"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "lives" ADD CONSTRAINT "lives_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "videos"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "live_messages" ADD CONSTRAINT "live_messages_liveId_fkey" FOREIGN KEY ("liveId") REFERENCES "lives"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "live_messages" ADD CONSTRAINT "live_messages_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
