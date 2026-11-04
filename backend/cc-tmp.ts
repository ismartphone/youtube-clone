import 'dotenv/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from './src/generated/prisma/client.js';
const prisma = new PrismaClient({ adapter: new PrismaPg({ connectionString: process.env['DATABASE_URL'] }) });
const user = await prisma.user.findUnique({ where: { email: 'viewer@example.com' }, select: { id: true } });
if (process.argv[2] === 'revoke') {
  console.log('retiré :', (await prisma.premiumSubscription.deleteMany({ where: { purchaseToken: { startsWith: 'essai-telechargement-' } } })).count);
} else {
  await prisma.premiumSubscription.create({ data: { userId: user!.id, purchaseToken: 'essai-telechargement-' + Date.now(), expiresAt: new Date(Date.now() + 30 * 86400000), amount: 499, currency: 'eur' } });
  console.log('abonnement de test donné à viewer');
}
await prisma.$disconnect();
