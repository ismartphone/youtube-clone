-- Compteur de « j'aime » dénormalisé, alimenté par un tampon Redis comme viewCount (ADR-004).
ALTER TABLE "videos" ADD COLUMN "likeCount" INTEGER NOT NULL DEFAULT 0;

-- Reprise des « j'aime » déjà enregistrés.
UPDATE "videos" SET "likeCount" = (
  SELECT COUNT(*) FROM "likes" WHERE "likes"."videoId" = "videos"."id"
);
