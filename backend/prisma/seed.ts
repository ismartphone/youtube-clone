// Données fictives de développement. Lancement : npx prisma db seed
// Le script vide d'abord les tables : ne jamais l'exécuter sur une base de production.
import 'dotenv/config';
import * as argon2 from 'argon2';
import { randomBytes } from 'node:crypto';
import { PrismaPg } from '@prisma/adapter-pg';
import {
  PrismaClient,
  Role,
  VideoFormat,
} from '../src/generated/prisma/client.js';
import {
  CC_CREATORS,
  CC_VIDEOS,
  ccAvatarUrl,
  ccHandle,
  ccOwnerEmail,
  ccVideoUrl,
} from './cc-videos.js';
import { seedBannerUrl } from './seed-banners.js';
import { LOCAL_SEED_VIDEOS, localVideoUrl } from './seed-videos.js';

const prisma = new PrismaClient({
  adapter: new PrismaPg({ connectionString: process.env['DATABASE_URL'] }),
});

// Fichiers MP4 de test publics (test-videos.co.uk, Blender), à remplacer à terme par des vidéos
// du stockage local.
const TEST_VIDEOS = 'https://test-videos.co.uk/vids';

// Mot de passe commun aux comptes de démonstration, haché comme à l'inscription (argon2id).
const SEED_PASSWORD = 'Youtube2026!';

function hashPassword() {
  return argon2.hash(SEED_PASSWORD, { type: argon2.argon2id });
}

// Date de publication relative au jour du seed, pour garder un catalogue récent.
function daysAgo(days: number) {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000);
}

// Miniature de remplacement au format de la vidéo : paysage pour LONG, portrait pour SHORT.
// Après le seed, « npm run thumbnails:regenerate » les remplace par une image extraite de chaque vidéo.
function thumbnail(title: string, format: VideoFormat) {
  const size = format === VideoFormat.SHORT ? '720x1280' : '1280x720';
  return `https://placehold.co/${size}.jpg?text=${encodeURIComponent(title)}`;
}

async function main() {
  await prisma.$transaction([
    prisma.watchHistory.deleteMany(),
    prisma.premiumSubscription.deleteMany(),
    prisma.follow.deleteMany(),
    prisma.like.deleteMany(),
    prisma.comment.deleteMany(),
    prisma.video.deleteMany(),
    prisma.category.deleteMany(),
    prisma.channel.deleteMany(),
    prisma.user.deleteMany(),
  ]);

  const [animation, nature, voyage, gaming, documentaire, musique] =
    await Promise.all(
      [
        'Animation',
        'Nature',
        'Voyage',
        'Gaming',
        'Documentaire',
        'Musique',
      ].map((name) => prisma.category.create({ data: { name } })),
    );
  const ccCategories = {
    Gaming: gaming.id,
    Documentaire: documentaire.id,
    Musique: musique.id,
  };

  const studio = await prisma.user.create({
    data: {
      email: 'studio@example.com',
      passwordHash: await hashPassword(),
      displayName: 'Studio Libre',
      role: Role.CREATOR,
      channel: {
        create: {
          name: 'Studio Libre',
          handle: 'studiolibre',
          description: "Extraits de films d'animation sous licence libre.",
          bannerUrl: seedBannerUrl('studiolibre'),
        },
      },
    },
    include: { channel: true },
  });

  const explorer = await prisma.user.create({
    data: {
      email: 'explorer@example.com',
      passwordHash: await hashPassword(),
      displayName: 'Carnet de Route',
      role: Role.CREATOR,
      channel: {
        create: {
          name: 'Carnet de Route',
          handle: 'carnetderoute',
          description: 'Balades en ville et en pleine nature.',
          bannerUrl: seedBannerUrl('carnetderoute'),
        },
      },
    },
    include: { channel: true },
  });

  const [viewer] = await prisma.user.createManyAndReturn({
    data: [
      {
        email: 'viewer@example.com',
        passwordHash: await hashPassword(),
        displayName: 'Camille Spectatrice',
        role: Role.USER,
      },
      {
        email: 'admin@example.com',
        passwordHash: await hashPassword(),
        displayName: 'Administrateur',
        role: Role.ADMIN,
      },
    ],
  });

  const studioChannelId = studio.channel!.id;
  const explorerChannelId = explorer.channel!.id;

  // Abonnements gratuits (FOLLOW) de démonstration. Camille suit Studio Libre mais pas
  // Carnet de Route, pour pouvoir montrer le bouton S'abonner. Admin ne suit personne.
  const follows = [
    { userId: viewer.id, channelId: studioChannelId },
    { userId: studio.id, channelId: explorerChannelId },
  ];
  await prisma.follow.createMany({ data: follows });

  const videos = [
    {
      channelId: studioChannelId,
      categoryId: animation.id,
      title: 'Sintel, la bande-annonce',
      description: 'Une jeune femme part à la recherche de son dragon.',
      videoUrl:
        'https://download.blender.org/durian/trailer/sintel_trailer-480p.mp4',
      durationSeconds: 52,
      publishedAt: daysAgo(13),
      format: VideoFormat.LONG,
    },
    {
      channelId: studioChannelId,
      categoryId: animation.id,
      title: 'Big Buck Bunny, extrait',
      description: 'Un lapin géant contre trois rongeurs farceurs.',
      videoUrl: `${TEST_VIDEOS}/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4`,
      durationSeconds: 10,
      publishedAt: daysAgo(12),
      format: VideoFormat.LONG,
    },
    {
      channelId: studioChannelId,
      categoryId: animation.id,
      title: 'Sintel en haute définition',
      videoUrl: `${TEST_VIDEOS}/sintel/mp4/h264/1080/Sintel_1080_10s_1MB.mp4`,
      durationSeconds: 10,
      publishedAt: daysAgo(10),
      format: VideoFormat.LONG,
    },
    {
      channelId: studioChannelId,
      categoryId: nature.id,
      title: 'Méduses en 10 secondes',
      videoUrl: `${TEST_VIDEOS}/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4`,
      durationSeconds: 10,
      publishedAt: daysAgo(9),
      format: VideoFormat.SHORT,
    },
    {
      channelId: studioChannelId,
      categoryId: nature.id,
      title: 'Le ballet des méduses',
      description: 'Plongée au milieu des méduses.',
      videoUrl: `${TEST_VIDEOS}/jellyfish/mp4/h264/720/Jellyfish_720_10s_5MB.mp4`,
      durationSeconds: 10,
      publishedAt: daysAgo(7),
      format: VideoFormat.LONG,
    },
    {
      channelId: explorerChannelId,
      categoryId: voyage.id,
      title: 'Une demi-minute en ville',
      description: 'La ville défile derrière la vitre du train.',
      videoUrl: localVideoUrl(LOCAL_SEED_VIDEOS.city),
      durationSeconds: Math.round(LOCAL_SEED_VIDEOS.city.clipSeconds),
      publishedAt: daysAgo(6),
      format: VideoFormat.LONG,
    },
    {
      channelId: explorerChannelId,
      categoryId: voyage.id,
      title: 'Cinq secondes de voyage',
      videoUrl: localVideoUrl(LOCAL_SEED_VIDEOS.travel),
      durationSeconds: Math.round(LOCAL_SEED_VIDEOS.travel.clipSeconds),
      publishedAt: daysAgo(4),
      format: VideoFormat.LONG,
    },
    {
      channelId: explorerChannelId,
      categoryId: voyage.id,
      title: 'Instantané de rue',
      videoUrl: localVideoUrl(LOCAL_SEED_VIDEOS.street),
      durationSeconds: Math.round(LOCAL_SEED_VIDEOS.street.clipSeconds),
      publishedAt: daysAgo(3),
      format: VideoFormat.SHORT,
    },
    {
      channelId: explorerChannelId,
      categoryId: nature.id,
      title: 'Respirer dehors',
      videoUrl: localVideoUrl(LOCAL_SEED_VIDEOS.breathe),
      durationSeconds: Math.round(LOCAL_SEED_VIDEOS.breathe.clipSeconds),
      publishedAt: daysAgo(1),
      format: VideoFormat.SHORT,
    },
    {
      channelId: explorerChannelId,
      categoryId: nature.id,
      title: 'Quelques secondes au grand air',
      videoUrl: localVideoUrl(LOCAL_SEED_VIDEOS.outdoors),
      durationSeconds: Math.round(LOCAL_SEED_VIDEOS.outdoors.clipSeconds),
      publishedAt: daysAgo(0),
      format: VideoFormat.LONG,
      // Vidéo exclusive (ADR-007), la plus récente : en tête du fil, le badge Premium et le
      // paywall se voient dès le premier lancement, sans passer par le back-office.
      isPremium: true,
    },
  ];

  await prisma.video.createMany({
    data: videos.map((video) => ({
      ...video,
      thumbnailUrl: thumbnail(video.title, video.format),
    })),
  });

  // Vidéos Creative Commons (cc-videos.ts) : une chaîne par créateur, avec un compte propriétaire
  // technique (mot de passe aléatoire). Les fichiers cc-*.mp4 sont fournis à la main ; sur un clone
  // neuf ils manquent, et ces entrées n'ont pas de fichier (voir docs/sources-videos.md).
  // « npm run seed:cc-videos » génère ensuite les avatars et remplace les miniatures provisoires.
  const ccChannelIds = new Map<string, string>();
  for (const creator of CC_CREATORS) {
    const owner = await prisma.user.create({
      data: {
        email: ccOwnerEmail(creator),
        passwordHash: await argon2.hash(randomBytes(32).toString('hex'), {
          type: argon2.argon2id,
        }),
        displayName: creator.name,
        role: Role.CREATOR,
        channel: {
          create: {
            name: creator.name,
            handle: ccHandle(creator),
            avatarUrl: ccAvatarUrl(creator),
          },
        },
      },
      include: { channel: true },
    });
    ccChannelIds.set(creator.name, owner.channel!.id);
  }
  await prisma.video.createMany({
    data: CC_VIDEOS.map((video, index) => ({
      channelId: ccChannelIds.get(video.creator.name)!,
      categoryId: ccCategories[video.category],
      title: video.title,
      videoUrl: ccVideoUrl(video),
      thumbnailUrl: thumbnail(video.title, video.format),
      durationSeconds: video.durationSeconds,
      format: VideoFormat[video.format],
      publishedAt: daysAgo(index + 1),
    })),
  });

  console.log(
    `Seed terminé : 6 catégories, ${4 + CC_CREATORS.length} utilisateurs, ${2 + CC_CREATORS.length} chaînes, ${follows.length} abonnements, ${videos.length + CC_VIDEOS.length} vidéos.`,
  );
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
