import { Logger } from '@nestjs/common';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const run = promisify(execFile);

const PROBE_TIMEOUT_MS = 30_000;
const VERSION_CHECK_TIMEOUT_MS = 5_000;

// Champs lus dans la sortie JSON de ffprobe (-show_format -show_streams).
export interface FfprobeOutput {
  format?: { duration?: string };
  streams?: {
    codec_type?: string;
    codec_name?: string;
    width?: number;
    height?: number;
    tags?: { rotate?: string };
    // Image de couverture intégrée (attached_pic = 1) : ce n'est pas le flux vidéo.
    disposition?: { attached_pic?: number };
    index?: number;
    side_data_list?: { rotation?: number }[];
  }[];
}

// Le fichier n'a pas pu être lu dans l'un des formats permis.
export class ProbeFailedError extends Error {}

/**
 * ffprobe appelé en ligne de commande (ADR-011), pour valider les fichiers envoyés depuis
 * l'application (ADR-015). Le fichier n'est lu que depuis le disque et que dans les formats
 * permis : un fichier déguisé (playlist, autre conteneur) est refusé par ffprobe lui-même,
 * quel que soit son nom ou le type annoncé par le client.
 * ffprobe absent : le serveur démarre quand même, avec une erreur claire dans les journaux.
 */
export class Ffprobe {
  private readonly logger: Logger;
  private available: Promise<boolean> | undefined;

  // [usage] : ce qui est désactivé sans ffprobe, pour le message des journaux.
  constructor(
    private readonly command: string,
    private readonly usage: string,
    context: string,
  ) {
    this.logger = new Logger(context);
  }

  // Vérifiée une seule fois, au démarrage ou au premier appel.
  isAvailable(): Promise<boolean> {
    this.available ??= run(this.command, ['-version'], {
      timeout: VERSION_CHECK_TIMEOUT_MS,
    }).then(
      () => true,
      () => {
        this.logger.error(
          `ffprobe est introuvable (commande « ${this.command} »). ${this.usage} est désactivé ; ` +
            "le reste de l'API fonctionne. Installez FFmpeg ou indiquez le chemin de ffprobe " +
            'dans FFPROBE_PATH, puis redémarrez.',
        );
        return false;
      },
    );
    return this.available;
  }

  /**
   * Analyse le fichier local [path] avec les seuls démultiplexeurs [formats] (liste séparée par
   * des virgules). Lève ProbeFailedError si ffprobe ne peut pas le lire ainsi.
   */
  async probe(path: string, formats: string): Promise<FfprobeOutput> {
    try {
      const { stdout } = await run(
        this.command,
        [
          '-v',
          'error',
          '-protocol_whitelist',
          'file',
          '-format_whitelist',
          formats,
          '-show_format',
          '-show_streams',
          '-of',
          'json',
          path,
        ],
        { timeout: PROBE_TIMEOUT_MS, maxBuffer: 10 * 1024 * 1024 },
      );
      return JSON.parse(stdout) as FfprobeOutput;
    } catch (error) {
      // Raison technique dans les journaux ; le client reçoit un message simple.
      const detail = error as { stderr?: string; message?: string };
      this.logger.warn(
        `Fichier refusé par ffprobe : ${detail.stderr?.trim().split('\n').at(-1) || detail.message}`,
      );
      throw new ProbeFailedError();
    }
  }
}
