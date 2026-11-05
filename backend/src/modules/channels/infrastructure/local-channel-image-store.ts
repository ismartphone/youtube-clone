import { OnModuleInit } from '@nestjs/common';
import { execFile } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { promisify } from 'node:util';
import {
  Ffprobe,
  ProbeFailedError,
} from '../../../shared/infrastructure/ffprobe.js';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import {
  ChannelImageUnavailableError,
  InvalidChannelImageError,
} from '../domain/channel.errors.js';
import type { ChannelImageKind } from '../domain/channel.js';
import type { ChannelImageStore } from '../domain/ports.js';

const run = promisify(execFile);

// Démultiplexeurs d'images seules, choisis par ffprobe d'après le contenu du fichier.
const IMAGE_FORMATS = 'jpeg_pipe,png_pipe';
const IMAGE_CODECS = ['mjpeg', 'png'];
const PROCESSING_TIMEOUT_MS = 30_000;

// Dossier et taille de sortie : photo carrée, bannière au rapport 3,6 de la page de chaîne.
const FORMATS: Record<
  ChannelImageKind,
  { folder: string; width: number; height: number }
> = {
  photo: { folder: 'channel-photos', width: 512, height: 512 },
  banner: { folder: 'channel-banners', width: 2048, height: 569 },
  // Logo d'annonceur du bandeau sponsorisé (ADR-021) : même traitement que la photo de chaîne.
  adLogo: { folder: 'ad-logos', width: 512, height: 512 },
  // Image d'un produit vendu pendant un live (ADR-029) : carrée, comme la photo de chaîne.
  product: { folder: 'product-images', width: 512, height: 512 },
};
// Nom généré par le serveur ; les avatars générés et les bannières du seed n'y ressemblent pas.
const UPLOADED_URL =
  /^\/media\/(channel-photos|channel-banners|ad-logos|product-images)\/[0-9a-f-]{36}\.jpg$/;

export interface ChannelImageConfig {
  ffmpeg: string;
  ffprobe: string;
  mediaDir: string;
}

/**
 * Photo de profil et bannière d'une chaîne (ADR-018) : validées par ffprobe d'après leur contenu
 * (JPEG ou PNG), jamais d'après l'extension, puis recadrées au centre par FFmpeg et enregistrées
 * en JPEG sous un nouveau nom à chaque envoi : le cache de 7 jours des clients ne sert jamais
 * l'ancienne image.
 */
export class LocalChannelImageStore implements ChannelImageStore, OnModuleInit {
  private readonly ffprobe: Ffprobe;

  constructor(private readonly config: ChannelImageConfig) {
    this.ffprobe = new Ffprobe(
      config.ffprobe,
      "L'envoi de photos et de bannières de chaîne",
      LocalChannelImageStore.name,
    );
  }

  async onModuleInit() {
    await this.ffprobe.isAvailable();
  }

  async save(path: string, kind: ChannelImageKind): Promise<string> {
    if (!(await this.ffprobe.isAvailable())) {
      throw new ChannelImageUnavailableError();
    }
    let output;
    try {
      output = await this.ffprobe.probe(path, IMAGE_FORMATS);
    } catch (error) {
      if (error instanceof ProbeFailedError) {
        throw new InvalidChannelImageError();
      }
      throw error;
    }
    const stream = output.streams?.find((s) => s.codec_type === 'video');
    if (
      !stream?.codec_name ||
      !IMAGE_CODECS.includes(stream.codec_name) ||
      !stream.width ||
      !stream.height
    ) {
      throw new InvalidChannelImageError();
    }

    const { folder, width, height } = FORMATS[kind];
    const directory = join(this.config.mediaDir, folder);
    await mkdir(directory, { recursive: true });
    const fileName = `${randomUUID()}.jpg`;
    const target = join(directory, fileName);
    try {
      await run(
        this.config.ffmpeg,
        [
          '-v',
          'error',
          '-y',
          '-i',
          path,
          '-vf',
          // Remplit le cadre sans déformer, puis coupe ce qui dépasse, au centre.
          `scale=${width}:${height}:force_original_aspect_ratio=increase,crop=${width}:${height}`,
          '-frames:v',
          '1',
          '-q:v',
          '3',
          target,
        ],
        { timeout: PROCESSING_TIMEOUT_MS },
      );
    } catch {
      await rm(target, { force: true });
      throw new InvalidChannelImageError();
    }
    return `${MEDIA_URL_PREFIX}/${folder}/${fileName}`;
  }

  isUploaded(url: string | null): boolean {
    return url !== null && UPLOADED_URL.test(url);
  }

  async remove(url: string): Promise<void> {
    if (!this.isUploaded(url)) {
      return;
    }
    await rm(
      join(this.config.mediaDir, url.slice(MEDIA_URL_PREFIX.length + 1)),
      {
        force: true,
      },
    );
  }
}
