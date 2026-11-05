// Produits de démonstration de Studio Libre (ADR-029) : de quoi épingler et acheter pendant un live.
// Prix normaux, entre 15 et 60 €. Les images (carré de couleur et nom du produit) sont dessinées
// par FFmpeg, comme les avatars des chaînes du seed ; rejouable sans doublon.
import { execFile } from 'node:child_process';
import { mkdir, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { promisify } from 'node:util';
import type { PrismaClient } from '../src/generated/prisma/client.js';
import { avatarColor } from '../src/modules/channels/domain/avatar.js';
import {
  ffmpegCommand,
  mediaDirectory,
} from '../src/shared/infrastructure/media.config.js';

const run = promisify(execFile);

// Prix en centimes. L'image porte le préfixe seed- : jamais supprimée par le back-office, qui ne
// supprime que les fichiers dont le nom est un UUID.
export const LIVE_PRODUCTS = [
  {
    key: 'casque-audio',
    name: 'Casque audio',
    price: 4990,
    description:
      'Casque sans fil à réduction de bruit, 30 heures d’autonomie, étui de transport inclus.',
  },
  {
    key: 'enceinte-portable',
    name: 'Enceinte portable',
    price: 3990,
    description:
      'Enceinte Bluetooth étanche, un son puissant partout, 12 heures de musique.',
  },
  {
    key: 'montre-connectee',
    name: 'Montre connectée',
    price: 5900,
    description:
      'Suivi du sport et du sommeil, notifications et écran lumineux, résistante à l’eau.',
  },
  {
    key: 'sac-a-dos',
    name: 'Sac à dos',
    price: 2990,
    description:
      'Sac à dos de 25 litres avec compartiment pour ordinateur, tissu déperlant.',
  },
  {
    key: 'lampe-de-bureau',
    name: 'Lampe de bureau',
    price: 1990,
    description:
      'Lampe LED à intensité réglable, trois températures de lumière, port de charge USB.',
  },
] as const;

const FONT_CANDIDATES = [
  process.env.AVATAR_FONT,
  'C:/Windows/Fonts/arialbd.ttf',
  '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
  '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
].filter((font): font is string => Boolean(font));

async function exists(file: string): Promise<boolean> {
  return stat(file).then(
    () => true,
    () => false,
  );
}

// Chemin relatif de l'image du produit, dessinée à la première demande.
async function ensureProductImage(
  key: string,
  name: string,
): Promise<string | null> {
  const folder = join(mediaDirectory(), 'product-images');
  await mkdir(folder, { recursive: true });
  const target = join(folder, `seed-${key}.jpg`);
  const url = `/media/product-images/seed-${key}.jpg`;
  if (await exists(target)) return url;
  let font: string | undefined;
  for (const candidate of FONT_CANDIDATES) {
    if (await exists(candidate)) {
      font = candidate;
      break;
    }
  }
  if (!font) return null;
  // Dans un filtre FFmpeg, « : » sépare les options et « ' » délimite le texte : on les échappe.
  const fontPath = font.replace(/\\/g, '/').replace(/:/g, '\\:');
  const text = name.replace(/[\\':]/g, (c) => `\\${c}`);
  await run(
    ffmpegCommand(),
    [
      '-v',
      'error',
      '-f',
      'lavfi',
      '-i',
      `color=c=0x${avatarColor(name)}:s=512x512:d=1`,
      '-frames:v',
      '1',
      '-vf',
      `drawtext=text='${text}':fontfile='${fontPath}':fontcolor=white:fontsize=46:x=(w-text_w)/2:y=(h-text_h)/2`,
      '-q:v',
      '3',
      '-y',
      target,
    ],
    { timeout: 15_000 },
  );
  return url;
}

/** Crée les produits de démonstration de [channelId] qui n'existent pas encore ; renvoie leur nombre. */
export async function seedLiveProducts(
  prisma: PrismaClient,
  channelId: string,
): Promise<number> {
  let created = 0;
  for (const product of LIVE_PRODUCTS) {
    const existing = await prisma.product.findFirst({
      where: { channelId, name: product.name },
      select: { id: true },
    });
    if (existing) continue;
    const imageUrl = await ensureProductImage(product.key, product.name);
    if (!imageUrl) {
      console.warn(
        `Image non générée pour « ${product.name} » (police introuvable : définissez AVATAR_FONT).`,
      );
      continue;
    }
    await prisma.product.create({
      data: {
        channelId,
        name: product.name,
        price: product.price,
        description: product.description,
        imageUrl,
      },
    });
    created += 1;
  }
  return created;
}
