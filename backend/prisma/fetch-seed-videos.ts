// Récupère les vidéos de démonstration du seed (prisma/seed-videos.ts) dans le stockage local.
// Lancement, après un clone : npm run seed:videos   (--force pour tout retélécharger)
//
// Pour chaque vidéo :
// 1. téléchargement depuis Pexels ou l'adresse de la source, dans un dossier temporaire vide ;
// 2. vérification de l'original avec ffprobe (flux vidéo, durée, orientation) et décodage complet,
//    pour écarter un fichier tronqué ;
// 3. réencodage en 720p H.264, 30 images/s au plus, coupé à la durée prévue, sans piste de données,
//    avec +faststart pour la lecture progressive (ADR-002) ;
// 4. vérification finale (durée, flux, décodage, extraction d'une image) avant de l'installer.
// Une vidéo déjà présente et valide est gardée. Nécessite FFmpeg et ffprobe (FFMPEG_PATH, FFPROBE_PATH).
// fetchVideoFile est aussi utilisée par videos:localize (localize-external-videos.ts).
import { execFile } from 'node:child_process';
import { createWriteStream } from 'node:fs';
import { copyFile, mkdir, mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import type { ReadableStream } from 'node:stream/web';
import { promisify } from 'node:util';
import {
  ffmpegCommand,
  ffprobeCommand,
  mediaDirectory,
} from '../src/shared/infrastructure/media.config.js';
import {
  LOCAL_SEED_VIDEOS,
  localVideoFileName,
  sourceUrl,
  type SeedVideoFile,
} from './seed-videos.js';

const run = promisify(execFile);

const TARGET_SHORT_SIDE = 720;
// Écart toléré entre la durée prévue et la durée réelle (arrondis du conteneur).
const DURATION_TOLERANCE_SECONDS = 0.5;

try {
  process.loadEnvFile();
} catch {
  // Pas de fichier .env : on garde les variables déjà définies.
}

const ffmpeg = ffmpegCommand();
const ffprobe = ffprobeCommand();

interface Probe {
  duration: number;
  videoStreams: { codec: string; width: number; height: number }[];
  otherStreams: string[]; // types des flux non vidéo et non audio (données, sous-titres...)
}

async function probe(file: string): Promise<Probe> {
  const { stdout } = await run(
    ffprobe,
    ['-v', 'error', '-show_format', '-show_streams', '-of', 'json', file],
    { maxBuffer: 10 * 1024 * 1024 },
  );
  const json = JSON.parse(stdout) as {
    format?: { duration?: string };
    streams?: {
      codec_type?: string;
      codec_name?: string;
      width?: number;
      height?: number;
    }[];
  };
  const streams = json.streams ?? [];
  return {
    duration: Number(json.format?.duration ?? 0),
    videoStreams: streams
      .filter((s) => s.codec_type === 'video')
      .map((s) => ({
        codec: s.codec_name ?? '?',
        width: s.width ?? 0,
        height: s.height ?? 0,
      })),
    otherStreams: streams
      .map((s) => s.codec_type ?? '?')
      .filter((type) => type !== 'video' && type !== 'audio'),
  };
}

// Décode toute la vidéo : un fichier tronqué ou corrompu échoue ici.
async function decodesFully(file: string): Promise<void> {
  try {
    await run(
      ffmpeg,
      [
        '-v',
        'error',
        '-xerror',
        '-i',
        file,
        '-map',
        '0:v:0',
        '-f',
        'null',
        '-',
      ],
      { maxBuffer: 10 * 1024 * 1024 },
    );
  } catch (error) {
    // Première erreur de FFmpeg seulement : la suite répète la même cause.
    const firstLine = (error as { stderr?: string }).stderr
      ?.trim()
      .split('\n')[0];
    throw new Error(`décodage impossible (${firstLine ?? 'erreur inconnue'})`);
  }
}

function hasOrientation(
  { width, height }: { width: number; height: number },
  orientation: SeedVideoFile['orientation'],
): boolean {
  return orientation === 'portrait' ? height > width : width > height;
}

async function checkSource(file: string, video: SeedVideoFile): Promise<Probe> {
  const info = await probe(file);
  const stream = info.videoStreams[0];
  if (!stream) throw new Error("aucun flux vidéo dans l'original");
  if (!video.cropToOrientation && !hasOrientation(stream, video.orientation)) {
    throw new Error(
      `orientation inattendue (${stream.width}x${stream.height}, attendu : ${video.orientation})`,
    );
  }
  if (info.duration <= 0) throw new Error('durée illisible');
  await decodesFully(file);
  return info;
}

async function checkFinal(
  file: string,
  video: SeedVideoFile,
  expectedSeconds: number,
): Promise<void> {
  const info = await probe(file);
  if (info.videoStreams.length !== 1) {
    throw new Error(`${info.videoStreams.length} flux vidéo au lieu d'un`);
  }
  if (info.otherStreams.length > 0) {
    throw new Error(`flux inattendus : ${info.otherStreams.join(', ')}`);
  }
  const stream = info.videoStreams[0];
  const shortSide = Math.min(stream.width, stream.height);
  if (
    stream.codec !== 'h264' ||
    shortSide !== TARGET_SHORT_SIDE ||
    !hasOrientation(stream, video.orientation)
  ) {
    throw new Error(
      `format inattendu : ${stream.codec} ${stream.width}x${stream.height}`,
    );
  }
  if (Math.abs(info.duration - expectedSeconds) > DURATION_TOLERANCE_SECONDS) {
    throw new Error(
      `durée ${info.duration.toFixed(1)} s au lieu de ${expectedSeconds} s`,
    );
  }
  await decodesFully(file);
  // Extraction d'une image à 10 % de la durée, comme le fera la génération de miniature.
  const frame = `${file}.check.jpg`;
  try {
    await run(ffmpeg, [
      '-v',
      'error',
      '-ss',
      (info.duration * 0.1).toFixed(2),
      '-i',
      file,
      '-frames:v',
      '1',
      '-y',
      frame,
    ]);
    if ((await stat(frame)).size === 0) throw new Error('image vide');
  } finally {
    await rm(frame, { force: true });
  }
  // Lecture progressive : l'index (moov) doit précéder les données (mdat).
  const bytes = await readFile(file);
  const moov = bytes.indexOf('moov');
  if (moov < 0 || moov > bytes.indexOf('mdat')) {
    throw new Error("l'index n'est pas en tête du fichier (+faststart)");
  }
}

// La source n'a pas pu être téléchargée : distincte d'un échec de traitement chez nous.
export class SourceUnavailableError extends Error {}

async function download(url: string, target: string): Promise<void> {
  let response;
  try {
    response = await fetch(url, { redirect: 'follow' });
  } catch (error) {
    throw new SourceUnavailableError(
      `source injoignable (${(error as Error).message})`,
    );
  }
  const type = response.headers.get('content-type') ?? '';
  if (!response.ok || !response.body || !type.startsWith('video/')) {
    throw new SourceUnavailableError(
      `téléchargement refusé (HTTP ${response.status}, ${type || 'type inconnu'})`,
    );
  }
  await pipeline(
    Readable.fromWeb(response.body as ReadableStream),
    createWriteStream(target),
  );
}

async function transcode(
  source: string,
  target: string,
  video: SeedVideoFile,
): Promise<void> {
  const scale =
    video.orientation === 'portrait'
      ? `scale=${TARGET_SHORT_SIDE}:-2`
      : `scale=-2:${TARGET_SHORT_SIDE}`;
  // Recadrage au centre au format 9:16 (ou 16:9) avant la mise à l'échelle.
  const crop = video.cropToOrientation
    ? video.orientation === 'portrait'
      ? 'crop=trunc(ih*9/16/2)*2:ih,'
      : 'crop=iw:trunc(iw*9/16/2)*2,'
    : '';
  await run(
    ffmpeg,
    [
      '-hide_banner',
      '-loglevel',
      'error',
      '-i',
      source,
      '-t',
      String(video.clipSeconds),
      '-map',
      '0:v:0',
      '-map',
      '0:a:0?',
      '-dn',
      // Les chapitres de la source deviendraient une piste de données dans le MP4.
      '-map_chapters',
      '-1',
      '-vf',
      crop + scale,
      '-fpsmax',
      '30',
      '-c:v',
      'libx264',
      '-preset',
      'medium',
      '-crf',
      '23',
      '-pix_fmt',
      'yuv420p',
      '-c:a',
      'aac',
      '-b:a',
      '128k',
      // Pas de piste de timecode recréée par le muxeur MP4.
      '-write_tmcd',
      '0',
      '-movflags',
      '+faststart',
      '-y',
      target,
    ],
    { maxBuffer: 10 * 1024 * 1024 },
  );
}

/**
 * Télécharge [video] dans [work], la vérifie, la réencode en 720p et la vérifie de nouveau, puis
 * l'installe en [target]. Renvoie sa durée réelle en secondes. Lève une erreur claire à la
 * première étape qui échoue ; rien n'est alors installé.
 */
export async function fetchVideoFile(
  video: SeedVideoFile,
  target: string,
  work: string,
): Promise<number> {
  const name = localVideoFileName(video);
  const original = join(work, `original-${name}`);
  const converted = join(work, name);
  try {
    await download(sourceUrl(video), original);
    const source = await checkSource(original, video);
    await transcode(original, converted, video);
    // La vidéo d'origine peut être plus courte que la durée prévue : elle est alors gardée entière.
    const duration = Math.min(video.clipSeconds, source.duration);
    await checkFinal(converted, video, duration);
    await copyFile(converted, target);
    return duration;
  } finally {
    await rm(original, { force: true });
    await rm(converted, { force: true });
  }
}

export async function checkTools(): Promise<boolean> {
  for (const command of [ffmpeg, ffprobe]) {
    try {
      await run(command, ['-version']);
    } catch {
      console.error(
        `« ${command} » est introuvable. Installez FFmpeg (par exemple « winget install Gyan.FFmpeg »), ` +
          'ou indiquez les chemins dans FFMPEG_PATH et FFPROBE_PATH.',
      );
      return false;
    }
  }
  return true;
}

async function exists(file: string): Promise<boolean> {
  return stat(file).then(
    () => true,
    () => false,
  );
}

async function main() {
  const force = process.argv.includes('--force');
  if (!(await checkTools())) {
    process.exitCode = 1;
    return;
  }

  const folder = join(mediaDirectory(), 'videos');
  await mkdir(folder, { recursive: true });
  // Les téléchargements arrivent dans un dossier temporaire vide, supprimé à la fin.
  const work = await mkdtemp(join(tmpdir(), 'seed-videos-'));
  const videos = Object.values(LOCAL_SEED_VIDEOS) as SeedVideoFile[];
  let failures = 0;
  try {
    for (const [index, video] of videos.entries()) {
      const name = localVideoFileName(video);
      const target = join(folder, name);
      const label = `[${index + 1}/${videos.length}] ${name}`;
      try {
        if (!force && (await exists(target))) {
          try {
            await checkFinal(target, video, video.clipSeconds);
            console.log(`${label} : déjà présente et valide`);
            continue;
          } catch (error) {
            console.log(
              `${label} : présente mais invalide (${(error as Error).message}), nouvelle récupération`,
            );
          }
        }
        await fetchVideoFile(video, target, work);
        const size = (await stat(target)).size / 1024 / 1024;
        console.log(`${label} : récupérée et vérifiée (${size.toFixed(1)} Mo)`);
      } catch (error) {
        failures++;
        console.error(`${label} : ÉCHEC, ${(error as Error).message}`);
      }
    }
  } finally {
    await rm(work, { recursive: true, force: true });
  }
  console.log(
    `\nTerminé : ${videos.length - failures} vidéo(s) prête(s), ${failures} échec(s). Dossier : ${folder}`,
  );
  process.exitCode = failures > 0 ? 1 : 0;
}

// Lancé directement (npm run seed:videos), et non importé par videos:localize.
if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  await main();
}
