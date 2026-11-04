-- DropForeignKey
ALTER TABLE "messages" DROP CONSTRAINT "messages_videoId_fkey";

-- CreateIndex
CREATE INDEX "messages_videoId_idx" ON "messages"("videoId");
