import { Logger, OnModuleInit } from '@nestjs/common';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import {
  InvalidVideoFileError,
  UploadUnavailableError,
  type ProbedVideo,
  type VideoProber,
} from '../domain/video-upload.js';

const run = promisify(execFile);

const PROBE_TIMEOUT_MS = 30_000;
const VERSION_CHECK_TIMEOUT_MS = 5_000;

interface FfprobeOutput {
  format?: { duration?: string };
  streams?: {
    codec_type?: string;
    width?: number;
    height?: number;
    tags?: { rotate?: string };
    side_data_list?: { rotation?: number }[];
  }[];
}

/**
 * Analyse d'une vidéo envoyée avec ffprobe, appelé en ligne de commande (ADR-011, ADR-015).
 * Le fichier n'est lu que comme MP4 (démultiplexeur « mov », qui couvre MP4 et MOV) et
 * uniquement depuis le disque : un fichier déguisé (playlist, image, autre conteneur)
 * est refusé par ffprobe lui-même, quel que soit son nom ou le type annoncé.
 * ffprobe absent : le serveur démarre quand même, et seul l'envoi est refusé.
 */
export class FfprobeVideoProber implements VideoProber, OnModuleInit {
  private readonly logger = new Logger(FfprobeVideoProber.name);
  private available: Promise<boolean> | undefined;

  constructor(private readonly command: string) {}

  async onModuleInit() {
    await this.isAvailable();
  }

  private isAvailable(): Promise<boolean> {
    this.available ??= run(this.command, ['-version'], {
      timeout: VERSION_CHECK_TIMEOUT_MS,
    }).then(
      () => true,
      () => {
        this.logger.error(
          `ffprobe est introuvable (commande « ${this.command} »). L'envoi de vidéos depuis ` +
            "l'application est désactivé ; le reste de l'API fonctionne. Installez FFmpeg ou " +
            'indiquez le chemin de ffprobe dans FFPROBE_PATH, puis redémarrez.',
        );
        return false;
      },
    );
    return this.available;
  }

  async probe(path: string): Promise<ProbedVideo> {
    if (!(await this.isAvailable())) {
      throw new UploadUnavailableError();
    }
    let output: FfprobeOutput;
    try {
      const { stdout } = await run(
        this.command,
        [
          '-v',
          'error',
          '-protocol_whitelist',
          'file',
          '-format_whitelist',
          'mov',
          '-show_format',
          '-show_streams',
          '-of',
          'json',
          path,
        ],
        { timeout: PROBE_TIMEOUT_MS, maxBuffer: 10 * 1024 * 1024 },
      );
      output = JSON.parse(stdout) as FfprobeOutput;
    } catch (error) {
      // Raison technique dans les journaux ; le créateur reçoit un message simple.
      const detail = error as { stderr?: string; message?: string };
      this.logger.warn(
        `Fichier refusé par ffprobe : ${detail.stderr?.trim().split('\n').at(-1) || detail.message}`,
      );
      throw new InvalidVideoFileError(
        "Ce fichier n'est pas une vidéo lisible. Envoyez une vidéo au format MP4.",
      );
    }
    const stream = output.streams?.find((s) => s.codec_type === 'video');
    const durationSeconds = Number(output.format?.duration ?? 0);
    if (!stream?.width || !stream.height || !(durationSeconds > 0)) {
      throw new InvalidVideoFileError(
        'Ce fichier ne contient pas de vidéo lisible.',
      );
    }
    // Les téléphones enregistrent souvent en paysage avec une consigne de rotation : une
    // vidéo tournée d'un quart de tour s'affiche avec la largeur et la hauteur inversées.
    const rotation =
      stream.side_data_list?.find((d) => d.rotation !== undefined)?.rotation ??
      Number(stream.tags?.rotate ?? 0);
    const quarterTurn = Math.abs(rotation) % 180 === 90;
    return {
      durationSeconds,
      width: quarterTurn ? stream.height : stream.width,
      height: quarterTurn ? stream.width : stream.height,
    };
  }
}
