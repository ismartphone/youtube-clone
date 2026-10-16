import { Logger, OnModuleInit } from '@nestjs/common';
import { execFile } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, rm, stat } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { promisify } from 'node:util';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import {
  ThumbnailGenerationError,
  ThumbnailUnavailableError,
  type ThumbnailGenerator,
  type ThumbnailSize,
} from '../domain/thumbnail-generator.js';

const run = promisify(execFile);

const THUMBNAILS_FOLDER = 'thumbnails';
const URL_PREFIX = `${MEDIA_URL_PREFIX}/${THUMBNAILS_FOLDER}/`;
// Nom de fichier généré : UUID + .jpg. Sert aussi à reconnaître nos miniatures.
const GENERATED_URL = /^\/media\/thumbnails\/[0-9a-f-]{36}\.jpg$/;

// Une vidéo distante peut être lente à joindre ; au-delà, on abandonne.
const EXTRACTION_TIMEOUT_MS = 60_000;
const VERSION_CHECK_TIMEOUT_MS = 5_000;

export interface FfmpegConfig {
  command: string;
  mediaDir: string;
}

/**
 * Extraction d'une image avec FFmpeg, appelé en ligne de commande (pas de dépendance npm).
 * FFmpeg absent : le serveur démarre quand même, avec une erreur claire dans les journaux,
 * et seule la génération de miniatures est refusée.
 */
export class FfmpegThumbnailGenerator
  implements ThumbnailGenerator, OnModuleInit
{
  private readonly logger = new Logger(FfmpegThumbnailGenerator.name);
  private available: Promise<boolean> | undefined;

  constructor(private readonly config: FfmpegConfig) {}

  async onModuleInit() {
    await this.isAvailable();
  }

  // Vérifiée une seule fois, au démarrage (ou au premier appel hors serveur, dans la commande).
  isAvailable(): Promise<boolean> {
    this.available ??= run(this.config.command, ['-version'], {
      timeout: VERSION_CHECK_TIMEOUT_MS,
    }).then(
      ({ stdout }) => {
        this.logger.log(`FFmpeg disponible : ${stdout.split('\n')[0]}`);
        return true;
      },
      () => {
        this.logger.error(
          `FFmpeg est introuvable (commande « ${this.config.command} »). La génération des miniatures ` +
            "est désactivée ; le reste de l'API fonctionne. Installez FFmpeg (par exemple " +
            '« winget install Gyan.FFmpeg ») ou indiquez son chemin dans FFMPEG_PATH, puis redémarrez.',
        );
        return false;
      },
    );
    return this.available;
  }

  async generate(
    videoUrl: string,
    atSeconds: number,
    { width, height }: ThumbnailSize,
  ): Promise<string> {
    if (!(await this.isAvailable())) {
      throw new ThumbnailUnavailableError();
    }
    const input = this.resolveInput(videoUrl);
    const folder = join(this.config.mediaDir, THUMBNAILS_FOLDER);
    await mkdir(folder, { recursive: true });
    // Nouveau nom à chaque génération : les clients ne gardent pas l'ancienne image en cache.
    const fileName = `${randomUUID()}.jpg`;
    const output = join(folder, fileName);
    try {
      await run(
        this.config.command,
        [
          '-hide_banner',
          '-loglevel',
          'error',
          '-protocol_whitelist',
          input.protocols,
          // -ss avant -i : recherche rapide, sans décoder toute la vidéo jusqu'à la position.
          '-ss',
          atSeconds.toFixed(2),
          '-i',
          input.source,
          '-frames:v',
          '1',
          // Taille exacte sans déformation : l'image couvre le cadre, puis l'excédent est coupé au centre.
          '-vf',
          `scale=${width}:${height}:force_original_aspect_ratio=increase,crop=${width}:${height}`,
          '-q:v',
          '3',
          '-y',
          output,
        ],
        { timeout: EXTRACTION_TIMEOUT_MS },
      );
      const { size } = await stat(output);
      if (size === 0) {
        throw new Error('fichier vide');
      }
    } catch (error) {
      await rm(output, { force: true });
      throw new ThumbnailGenerationError(describe(error));
    }
    return URL_PREFIX + fileName;
  }

  /**
   * Vidéo stockée chez nous (/media/videos/...) : lue directement sur le disque, sans repasser
   * par HTTP. Seul le protocole « file » est alors permis, et le chemin ne peut pas sortir du
   * dossier de stockage. Vidéo externe : seuls les protocoles réseau sont permis, pour qu'une URL
   * ne puisse pas faire lire un fichier du serveur.
   */
  private resolveInput(videoUrl: string): {
    source: string;
    protocols: string;
  } {
    if (!videoUrl.startsWith(`${MEDIA_URL_PREFIX}/`)) {
      return { source: videoUrl, protocols: 'http,https,tcp,tls' };
    }
    const root = resolve(this.config.mediaDir);
    const file = resolve(root, `.${videoUrl.slice(MEDIA_URL_PREFIX.length)}`);
    if (!file.startsWith(root + sep)) {
      throw new ThumbnailGenerationError('chemin de fichier invalide');
    }
    return { source: file, protocols: 'file' };
  }

  isGenerated(thumbnailUrl: string): boolean {
    return GENERATED_URL.test(thumbnailUrl);
  }

  async remove(thumbnailUrl: string): Promise<void> {
    if (!this.isGenerated(thumbnailUrl)) {
      return;
    }
    const fileName = thumbnailUrl.slice(URL_PREFIX.length);
    await rm(join(this.config.mediaDir, THUMBNAILS_FOLDER, fileName), {
      force: true,
    });
  }
}

// Message court pour l'administrateur : la dernière ligne d'erreur de FFmpeg, ou le délai dépassé.
function describe(error: unknown): string {
  const failure = error as {
    killed?: boolean;
    stderr?: string;
    message?: string;
  };
  if (failure.killed) {
    return "la vidéo n'a pas répondu à temps";
  }
  const lastLine = failure.stderr?.trim().split('\n').at(-1);
  return lastLine || failure.message || 'erreur inconnue';
}
