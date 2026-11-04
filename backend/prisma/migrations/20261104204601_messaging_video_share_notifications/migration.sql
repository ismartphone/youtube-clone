-- AlterTable
ALTER TABLE "messages" ADD COLUMN     "videoId" UUID;

-- AlterTable
ALTER TABLE "users" ADD COLUMN     "messageNotificationsEnabled" BOOLEAN NOT NULL DEFAULT true;

-- AddForeignKey
ALTER TABLE "messages" ADD CONSTRAINT "messages_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "videos"("id") ON DELETE SET NULL ON UPDATE CASCADE;
