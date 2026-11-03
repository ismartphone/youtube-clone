import { execFile } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, rm } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { promisify } from 'node:util';
import { Ffprobe } from '../../../shared/infrastructure/ffprobe.js';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import { hlsRungs, type HlsGenerator } from '../domain/hls.js';

const run = promisify(execFile);

const HLS_FOLDER = 'hls';
const VIDEOS_PREFIX = `${MEDIA_URL_PREFIX}/videos/`;
const URL_PREFIX = `${MEDIA_URL_PREFIX}/${HLS_FOLDER}/`;
const MASTER = 'master.m3u8';
// Dossier généré : identifiant aléatoire, distinct de celui de la vidéo (ADR-017).
const GENERATED_URL = /^\/media\/hls\/([0-9a-f-]{36})\/master\.m3u8$/;
// Segments d'environ 4 secondes ; une image clé est forcée à chaque début de segment.
const SEGMENT_SECONDS = 4;
const AUDIO_KBPS = 128;
// Une vidéo longue peut prendre plusieurs minutes ; au-delà d'une heure, on abandonne.
const ENCODING_TIMEOUT_MS = 60 * 60 * 1000;

export interface FfmpegHlsConfig {
  ffmpeg: string;
  ffprobe: string;
  mediaDir: string;
}

/**
 * Échelle HLS encodée par FFmpeg (ADR-017) : un manifeste principal et un manifeste par qualité,
 * chaque qualité dans son sous-dossier (1080p/, 720p/...). Le serveur sert ensuite ces fichiers
 * en statique, sans transcodage à la lecture.
 */
export class FfmpegHlsGenerator implements HlsGenerator {
  private readonly ffprobe: Ffprobe;

  constructor(private readonly config: FfmpegHlsConfig) {
    this.ffprobe = new Ffprobe(
      config.ffprobe,
      'La génération HLS',
      FfmpegHlsGenerator.name,
    );
  }

  isLocal(videoUrl: string): boolean {
    return videoUrl.startsWith(VIDEOS_PREFIX);
  }

  async generate(videoUrl: string): Promise<string> {
    const source = this.localPath(videoUrl);
    const probe = await this.ffprobe.probe(source, 'mov');
    // Premier flux vidéo qui n'est pas une image de couverture intégrée.
    const video = probe.streams?.find(
      (s) => s.codec_type === 'video' && !s.disposition?.attached_pic,
    );
    if (!video?.width || !video.height || video.index === undefined) {
      throw new Error('aucun flux vidéo dans le fichier');
    }
    const hasAudio = probe.streams?.some((s) => s.codec_type === 'audio');
    const rotation =
      video.side_data_list?.find((d) => d.rotation !== undefined)?.rotation ??
      Number(video.tags?.rotate ?? 0);
    const quarterTurn = Math.abs(rotation) % 180 === 90;
    // Dimensions affichées : FFmpeg applique la rotation avant les filtres.
    const width = quarterTurn ? video.height : video.width;
    const height = quarterTurn ? video.width : video.height;
    const portrait = height > width;
    const rungs = hlsRungs(width, height);

    const id = randomUUID();
    const folder = join(this.config.mediaDir, HLS_FOLDER, id);
    await mkdir(folder, { recursive: true });
    // Chaque qualité est mise à l'échelle sur son petit côté, l'autre suivant les proportions.
    const scales = rungs
      .map((rung, i) => {
        const scale = portrait ? `${rung.size}:-2` : `-2:${rung.size}`;
        return `[v${i}]scale=${scale}[s${i}]`;
      })
      .join(';');
    const filter = `[0:${video.index}]split=${rungs.length}${rungs.map((_, i) => `[v${i}]`).join('')};${scales}`;
    const outputs = rungs.flatMap((rung, i) => [
      '-map',
      `[s${i}]`,
      `-c:v:${i}`,
      'libx264',
      `-b:v:${i}`,
      `${rung.videoKbps}k`,
      `-maxrate:v:${i}`,
      `${rung.videoKbps}k`,
      `-bufsize:v:${i}`,
      `${rung.videoKbps * 2}k`,
      ...(hasAudio
        ? ['-map', '0:a:0', `-c:a:${i}`, 'aac', `-b:a:${i}`, `${AUDIO_KBPS}k`]
        : []),
    ]);
    const streamMap = rungs
      .map((rung, i) => `v:${i}${hasAudio ? `,a:${i}` : ''},name:${rung.size}p`)
      .join(' ');
    try {
      await run(
        this.config.ffmpeg,
        [
          '-v',
          'error',
          '-y',
          '-i',
          source,
          '-filter_complex',
          filter,
          ...outputs,
          '-preset',
          'veryfast',
          '-sc_threshold',
          '0',
          '-force_key_frames',
          `expr:gte(t,n_forced*${SEGMENT_SECONDS})`,
          '-f',
          'hls',
          '-hls_time',
          String(SEGMENT_SECONDS),
          '-hls_playlist_type',
          'vod',
          '-hls_segment_filename',
          join(folder, '%v', 'seg_%03d.ts'),
          '-master_pl_name',
          MASTER,
          '-var_stream_map',
          streamMap,
          join(folder, '%v', 'index.m3u8'),
        ],
        { timeout: ENCODING_TIMEOUT_MS, maxBuffer: 10 * 1024 * 1024 },
      );
    } catch (error) {
      await rm(folder, { recursive: true, force: true });
      const failure = error as { stderr?: string; message?: string };
      throw new Error(
        failure.stderr?.trim().split('\n').at(-1) || failure.message,
      );
    }
    return `${URL_PREFIX}${id}/${MASTER}`;
  }

  async remove(hlsUrl: string): Promise<void> {
    const id = GENERATED_URL.exec(hlsUrl)?.[1];
    if (id) {
      await rm(join(this.config.mediaDir, HLS_FOLDER, id), {
        recursive: true,
        force: true,
      });
    }
  }

  // Le chemin ne peut pas sortir du dossier de stockage.
  private localPath(videoUrl: string): string {
    if (!this.isLocal(videoUrl)) {
      throw new Error('vidéo externe : pas de HLS');
    }
    const root = resolve(this.config.mediaDir);
    const file = resolve(root, `.${videoUrl.slice(MEDIA_URL_PREFIX.length)}`);
    if (!file.startsWith(root + sep)) {
      throw new Error('chemin de fichier invalide');
    }
    return file;
  }
}
