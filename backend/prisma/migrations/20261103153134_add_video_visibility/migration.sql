-- CreateEnum
CREATE TYPE "VideoVisibility" AS ENUM ('PUBLIC', 'UNLISTED', 'PRIVATE');

-- AlterTable
ALTER TABLE "videos" ADD COLUMN     "visibility" "VideoVisibility" NOT NULL DEFAULT 'PUBLIC';
