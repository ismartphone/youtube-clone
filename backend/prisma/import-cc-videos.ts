// Ajoute les vidéos Creative Commons (prisma/cc-videos.ts) à la base existante, sans rien effacer.
// Lancement : npm run seed:cc-videos
//
// Idempotente : une chaîne ou une vidéo déjà présente n'est pas recréée, on peut la relancer.
// Pour chaque vidéo :
// - le fichier de storage/media/videos est vérifié par ffprobe (flux vidéo, durée, orientation
//   conforme au format) ; absent ou non conforme, la vidéo est signalée et passée ;
// - la chaîne de son créateur est créée si besoin, avec un compte propriétaire et un avatar
//   (initiales sur fond de couleur) généré dans storage/media/avatars ;
// - l'échelle HLS multi-qualités et les sous-titres sont générés (ADR-017), y compris pour une
//   vidéo déjà présente qui n'en a pas ;
// - la miniature est générée par le service FFmpeg du backend, réutilisé tel quel. Une vidéo
//   déjà présente avec une miniature de remplacement (seed) reçoit la sienne.
// Nécessite FFmpeg et ffprobe (FFMPEG_PATH, FFPROBE_PATH), et la base à jour (migrations).
import 'dotenv/config';
import * as argon2 from 'argon2';
import { execFile } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient, Role } from '../src/generated/prisma/client.js';
import {
  isPlaceholderThumbnail,
  thumbnailPosition,
  thumbnailSize,
} from '../src/modules/videos/domain/thumbnail-generator.js';
import { SUBTITLES_DISABLED_FOR } from '../src/modules/videos/domain/subtitles.js';
import { FfmpegHlsGenerator } from '../src/modules/videos/infrastructure/ffmpeg-hls-generator.js';
import { FfmpegSubtitleGenerator } from '../src/modules/videos/infrastructure/ffmpeg-subtitle-generator.js';
import { FfmpegThumbnailGenerator } from '../src/modules/videos/infrastructure/ffmpeg-thumbnail-generator.js';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
  whisperModelPath,
} from '../src/shared/infrastructure/media.config.js';
import {
  CC_VIDEOS,
  ccAvatarUrl,
  ccHandle,
  ccOwnerEmail,
  ccVideoUrl,
  ensureCcAvatar,
  type CcCreator,
  type CcVideo,
} from './cc-videos.js';

const run = promisify(execFile);

// Écart toléré entre la durée mesurée et celle de la liste.
const DURATION_TOLERANCE_SECONDS = 2;

const prisma = new PrismaClient({
  adapter: new PrismaPg({ connectionString: process.env['DATABASE_URL'] }),
});
const mediaDir = mediaDirectory();
const hls = new FfmpegHlsGenerator({
  ffmpeg: ffmpegCommand(),
  ffprobe: ffprobeCommand(),
  mediaDir,
});

// Échelle HLS de la vidéo (ADR-017). Un échec est signalé sans arrêter l'import : la vidéo
// reste lisible en MP4, et « npm run hls:generate » pourra la reprendre.
async function generateHls(videoId: string, video: CcVideo) {
  const startedAt = Date.now();
  try {
    const hlsUrl = await hls.generate(ccVideoUrl(video));
    await prisma.video.update({ where: { id: videoId }, data: { hlsUrl } });
    console.log(
      `  HLS généré (${Math.round((Date.now() - startedAt) / 1000)} s)`,
    );
  } catch (error) {
    console.warn(
      `  ✗ HLS non généré, lue en MP4 : ${(error as Error).message}`,
    );
  }
}

const subtitles = new FfmpegSubtitleGenerator({
  ffmpeg: ffmpegCommand(),
  ffprobe: ffprobeCommand(),
  mediaDir,
  model: whisperModelPath(),
});

// Sous-titres de la vidéo (ADR-017). Un échec ou un abandon est signalé sans arrêter l'import :
// la vidéo reste sans sous-titres, et « npm run subtitles:generate » pourra la reprendre.
async function generateSubtitles(videoId: string, video: CcVideo) {
  if (SUBTITLES_DISABLED_FOR.includes(ccVideoUrl(video))) {
    console.log('  pas de sous-titres : transcription inexploitable (ADR-017)');
    return;
  }
  const startedAt = Date.now();
  try {
    const subtitleUrl = await subtitles.generate(
      ccVideoUrl(video),
      video.durationSeconds,
    );
    await prisma.video.update({
      where: { id: videoId },
      data: { subtitleUrl },
    });
    console.log(
      `  sous-titres générés (${Math.round((Date.now() - startedAt) / 1000)} s)`,
    );
  } catch (error) {
    console.warn(`  ✗ sous-titres non générés : ${(error as Error).message}`);
  }
}

const thumbnails = new FfmpegThumbnailGenerator({
  command: ffmpegCommand(),
  mediaDir,
});

// Vérifie le fichier avec ffprobe ; renvoie la raison du refus, ou null s'il convient.
async function checkFile(video: CcVideo): Promise<string | null> {
  let json: {
    format?: { duration?: string };
    streams?: {
      codec_type?: string;
      width?: number;
      height?: number;
      disposition?: { attached_pic?: number };
      side_data_list?: { rotation?: number }[];
    }[];
  };
  try {
    const { stdout } = await run(
      ffprobeCommand(),
      [
        '-v',
        'error',
        '-show_format',
        '-show_streams',
        '-of',
        'json',
        join(mediaDir, 'videos', video.file),
      ],
      { maxBuffer: 10 * 1024 * 1024 },
    );
    json = JSON.parse(stdout) as typeof json;
  } catch {
    return 'fichier absent ou illisible';
  }
  // Image de couverture éventuelle (attached_pic) : ce n'est pas le flux vidéo.
  const stream = json.streams?.find(
    (s) => s.codec_type === 'video' && !s.disposition?.attached_pic,
  );
  if (!stream?.width || !stream.height) return 'aucun flux vidéo';
  const duration = Number(json.format?.duration ?? 0);
  if (Math.abs(duration - video.durationSeconds) > DURATION_TOLERANCE_SECONDS) {
    return `durée inattendue (${duration.toFixed(1)} s au lieu de ${video.durationSeconds} s)`;
  }
  const rotation =
    stream.side_data_list?.find((d) => d.rotation !== undefined)?.rotation ?? 0;
  const quarterTurn = Math.abs(rotation) % 180 === 90;
  const portrait = quarterTurn
    ? stream.width > stream.height
    : stream.height > stream.width;
  if (portrait !== (video.format === 'SHORT')) {
    return `orientation ${portrait ? 'portrait' : 'paysage'} incompatible avec le format ${video.format}`;
  }
  return null;
}

// Chaîne du créateur, créée avec son compte propriétaire si elle n'existe pas.
async function ensureChannel(creator: CcCreator): Promise<string | null> {
  const handle = ccHandle(creator);
  const email = ccOwnerEmail(creator);
  const existing = await prisma.channel.findUnique({
    where: { handle },
    select: { id: true, owner: { select: { email: true } } },
  });
  if (existing) {
    // Handle déjà pris par une autre chaîne que celle de ce créateur : on ne la détourne pas.
    if (existing.owner.email !== email) {
      console.warn(`  ✗ handle @${handle} déjà utilisé par une autre chaîne`);
      return null;
    }
    return existing.id;
  }
  if (await ensureCcAvatar(creator, ffmpegCommand(), mediaDir)) {
    console.log(`  avatar créé : ${ccAvatarUrl(creator)}`);
  }
  const owner = await prisma.user.upsert({
    where: { email },
    update: {},
    create: {
      email,
      // Compte technique : mot de passe aléatoire, jamais communiqué.
      passwordHash: await argon2.hash(randomBytes(32).toString('hex'), {
        type: argon2.argon2id,
      }),
      displayName: creator.name,
      role: Role.CREATOR,
    },
  });
  const channel = await prisma.channel.create({
    data: {
      ownerId: owner.id,
      name: creator.name,
      handle,
      avatarUrl: ccAvatarUrl(creator),
    },
  });
  console.log(`  chaîne créée : ${creator.name} (@${handle})`);
  return channel.id;
}

async function generateThumbnail(video: CcVideo): Promise<string> {
  return thumbnails.generate(
    ccVideoUrl(video),
    thumbnailPosition(video.durationSeconds),
    thumbnailSize(video.format),
  );
}

async function main() {
  if (!(await thumbnails.isAvailable())) {
    throw new Error(
      'FFmpeg est introuvable : installez-le ou indiquez FFMPEG_PATH.',
    );
  }
  const categories = new Map(
    (await prisma.category.findMany()).map((category) => [
      category.name,
      category.id,
    ]),
  );
  let created = 0;
  let skipped = 0;
  for (const [index, video] of CC_VIDEOS.entries()) {
    console.log(`${video.file} — ${video.title}`);
    const problem = await checkFile(video);
    if (problem) {
      console.warn(`  ✗ passée : ${problem}`);
      skipped++;
      continue;
    }
    const categoryId = categories.get(video.category);
    if (!categoryId) {
      console.warn(
        `  ✗ passée : catégorie « ${video.category} » absente de la base`,
      );
      skipped++;
      continue;
    }
    // L'avatar peut manquer même si la chaîne existe déjà (chaîne créée par le seed).
    await ensureCcAvatar(video.creator, ffmpegCommand(), mediaDir);
    const channelId = await ensureChannel(video.creator);
    if (!channelId) {
      skipped++;
      continue;
    }
    const existing = await prisma.video.findFirst({
      where: { videoUrl: ccVideoUrl(video) },
      select: { id: true, thumbnailUrl: true, hlsUrl: true, subtitleUrl: true },
    });
    if (existing) {
      if (isPlaceholderThumbnail(existing.thumbnailUrl)) {
        await prisma.video.update({
          where: { id: existing.id },
          data: { thumbnailUrl: await generateThumbnail(video) },
        });
        console.log('  déjà présente : miniature générée');
      } else {
        console.log('  déjà présente');
      }
      if (!existing.hlsUrl) await generateHls(existing.id, video);
      if (!existing.subtitleUrl) await generateSubtitles(existing.id, video);
      continue;
    }
    const createdVideo = await prisma.video.create({
      data: {
        channelId,
        categoryId,
        title: video.title,
        description: null,
        videoUrl: ccVideoUrl(video),
        thumbnailUrl: await generateThumbnail(video),
        durationSeconds: video.durationSeconds,
        format: video.format,
        // Dates étalées sur la dernière semaine, pour ne pas toutes arriver en tête du fil.
        publishedAt: new Date(Date.now() - (index + 1) * 24 * 60 * 60 * 1000),
      },
    });
    console.log('  ✓ vidéo créée');
    created++;
    await generateHls(createdVideo.id, video);
    await generateSubtitles(createdVideo.id, video);
  }
  console.log(`Terminé : ${created} vidéo(s) créée(s), ${skipped} passée(s).`);
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
