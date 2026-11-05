// Abonnés de démonstration de Studio Libre (ADR-029, D13) : 49 comptes fictifs qui, avec
// « Camille Spectatrice », portent la chaîne à 50 abonnés, le seuil pour démarrer un live.
// Ils apparaissent dans la recherche de la messagerie et dans le back-office : noms plausibles
// et avatars générés comme ceux des chaînes (initiales sur fond de couleur, par FFmpeg).
import * as argon2 from 'argon2';
import { randomBytes } from 'node:crypto';
import { stat } from 'node:fs/promises';
import { join } from 'node:path';
import type { PrismaClient } from '../src/generated/prisma/client.js';
import { FfmpegAvatarGenerator } from '../src/modules/channels/infrastructure/ffmpeg-avatar-generator.js';
import {
  ffmpegCommand,
  mediaDirectory,
} from '../src/shared/infrastructure/media.config.js';

export const LIVE_FAN_NAMES = [
  'Léa Martin',
  'Hugo Bernard',
  'Chloé Dubois',
  'Louis Thomas',
  'Manon Robert',
  'Jules Richard',
  'Inès Petit',
  'Nathan Durand',
  'Camille Leroy',
  'Lucas Moreau',
  'Sarah Simon',
  'Théo Laurent',
  'Emma Lefebvre',
  'Maxime Michel',
  'Clara Garcia',
  'Antoine David',
  'Julie Bertrand',
  'Alexandre Roux',
  'Marion Vincent',
  'Romain Fournier',
  'Lola Morel',
  'Baptiste Girard',
  'Anaïs André',
  'Thomas Lefèvre',
  'Pauline Mercier',
  'Quentin Dupont',
  'Océane Lambert',
  'Valentin Bonnet',
  'Eva François',
  'Adrien Martinez',
  'Élise Legrand',
  'Mathis Garnier',
  'Zoé Faure',
  'Kévin Rousseau',
  'Margaux Blanc',
  'Paul Guerin',
  'Juliette Muller',
  'Enzo Henry',
  'Noémie Roussel',
  'Clément Nicolas',
  'Lucie Perrin',
  'Arthur Morin',
  'Mélissa Mathieu',
  'Dylan Clement',
  'Charlotte Gauthier',
  'Raphaël Dumont',
  'Alice Lopez',
  'Simon Fontaine',
  'Ambre Chevalier',
] as const;

// « Léa Martin » → « lea.martin » : sans accents, pour l'email et le nom du fichier d'avatar.
export function fanSlug(name: string): string {
  return name
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '.')
    .replace(/^\.|\.$/g, '');
}

export function fanEmail(name: string): string {
  return `${fanSlug(name)}@fans.example.com`;
}

const avatarKey = (name: string) => `fan-${fanSlug(name)}`;

// Avatar déjà présent : gardé, comme pour les chaînes du seed. null si FFmpeg échoue (journalisé
// par le générateur) : le compte garde alors son avatar par défaut.
async function ensureAvatar(name: string): Promise<string | null> {
  const key = avatarKey(name);
  const mediaDir = mediaDirectory();
  const present = await stat(join(mediaDir, 'avatars', `${key}.png`)).then(
    () => true,
    () => false,
  );
  if (present) {
    return `/media/avatars/${key}.png`;
  }
  return new FfmpegAvatarGenerator({
    command: ffmpegCommand(),
    mediaDir,
  }).generate(name, key);
}

/**
 * Crée les comptes fictifs et les abonne à la chaîne [channelId]. Rejouable sans doublon : un
 * compte existant (même email) est gardé, un abonnement existant aussi.
 */
export async function seedLiveFans(
  prisma: PrismaClient,
  channelId: string,
): Promise<{ created: number; followers: number }> {
  // Un seul mot de passe aléatoire haché une fois : ces comptes ne servent pas à se connecter.
  const passwordHash = await argon2.hash(randomBytes(32).toString('hex'), {
    type: argon2.argon2id,
  });
  let created = 0;
  const ids: string[] = [];
  for (const name of LIVE_FAN_NAMES) {
    const email = fanEmail(name);
    const existing = await prisma.user.findUnique({
      where: { email },
      select: { id: true },
    });
    if (existing) {
      ids.push(existing.id);
      continue;
    }
    const user = await prisma.user.create({
      data: {
        email,
        passwordHash,
        displayName: name,
        avatarUrl: await ensureAvatar(name),
      },
      select: { id: true },
    });
    ids.push(user.id);
    created += 1;
  }
  await prisma.follow.createMany({
    data: ids.map((userId) => ({ userId, channelId })),
    skipDuplicates: true,
  });
  return {
    created,
    followers: await prisma.follow.count({ where: { channelId } }),
  };
}
