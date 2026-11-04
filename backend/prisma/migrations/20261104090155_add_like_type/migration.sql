-- CreateEnum
CREATE TYPE "LikeType" AS ENUM ('LIKE', 'DISLIKE');

-- AlterTable
ALTER TABLE "likes" ADD COLUMN     "type" "LikeType" NOT NULL DEFAULT 'LIKE';
