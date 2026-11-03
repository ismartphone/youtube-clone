-- Montant et devise réellement payés, enregistrés à l'achat.
-- Les abonnements existants ont tous été payés au seul prix pratiqué jusqu'ici : 4,99 €.
-- Le défaut ne sert qu'à remplir ces lignes ; il est retiré ensuite, pour que chaque achat
-- enregistre explicitement ce qui a été payé.
ALTER TABLE "premium_subscriptions" ADD COLUMN "amount" INTEGER NOT NULL DEFAULT 499;
ALTER TABLE "premium_subscriptions" ADD COLUMN "currency" TEXT NOT NULL DEFAULT 'eur';
ALTER TABLE "premium_subscriptions" ALTER COLUMN "amount" DROP DEFAULT;
ALTER TABLE "premium_subscriptions" ALTER COLUMN "currency" DROP DEFAULT;
