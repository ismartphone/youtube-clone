// Ajoute à une base existante les produits de démonstration de Studio Libre (ADR-029), sans vider
// quoi que ce soit : rejouable sans doublon. Le seed complet (npx prisma db seed) le fait déjà ;
// ce script sert à rattraper une base déjà remplie.
// Lancement : npm run seed:live-products
import 'dotenv/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from '../src/generated/prisma/client.js';
import { seedLiveProducts } from './live-products.js';

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
  const created = await seedLiveProducts(prisma, channel.id);
  console.log(`${created} produit(s) créé(s) pour Studio Libre.`);
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
