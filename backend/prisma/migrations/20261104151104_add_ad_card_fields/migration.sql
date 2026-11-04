-- AlterTable
ALTER TABLE "ads" ADD COLUMN     "advertiserName" TEXT,
ADD COLUMN     "ctaLabel" TEXT NOT NULL DEFAULT 'Visiter le site',
ADD COLUMN     "description" TEXT,
ADD COLUMN     "logoUrl" TEXT;
