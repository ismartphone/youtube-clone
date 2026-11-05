// Ajoute à une base existante les 49 abonnés de démonstration de Studio Libre (ADR-029, D13),
// sans vider quoi que ce soit : rejouable sans doublon. Le seed complet (npx prisma db seed) le
// fait déjà ; ce script sert à rattraper une base déjà remplie.
// Lancement : npm run seed:live-fans
import 'dotenv/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from '../src/generated/prisma/client.js';
import { seedLiveFans } from './live-fans.js';

const prisma = new PrismaClient({
  adapter: new PrismaPg({ connectionString: process.env['DATABASE_URL'] }),
});

async function main() {
  const channel = await prisma.channel.findUnique({
    where: { handle: 'studiolibre' },
    select: { id: true },
  });
  if (!channel) {
    throw new Error('Chaîne « studiolibre » introuvable : lancez le seed.');
  }
  const { created, followers } = await seedLiveFans(prisma, channel.id);
  console.log(
    `${created} compte(s) créé(s) ; Studio Libre compte ${followers} abonné(s).`,
  );
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
