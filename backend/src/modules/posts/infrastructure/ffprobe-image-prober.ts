import { OnModuleInit } from '@nestjs/common';
import {
  Ffprobe,
  ProbeFailedError,
} from '../../../shared/infrastructure/ffprobe.js';
import {
  ImageUnavailableError,
  InvalidImageError,
} from '../domain/post.errors.js';
import type { ImageFormat } from '../domain/post.js';
import type { ImageProber } from '../domain/ports.js';

// Démultiplexeurs d'images seules, choisis par ffprobe d'après le contenu du fichier.
const IMAGE_FORMATS = 'jpeg_pipe,png_pipe';

const CODECS: Record<string, ImageFormat> = { mjpeg: 'jpeg', png: 'png' };

/**
 * Analyse de l'image d'un post avec ffprobe (ADR-011, ADR-015) : seules une image JPEG ou PNG
 * sont acceptées, d'après leur contenu, jamais d'après l'extension ou le type annoncé.
 */
export class FfprobeImageProber implements ImageProber, OnModuleInit {
  private readonly ffprobe: Ffprobe;

  constructor(command: string) {
    this.ffprobe = new Ffprobe(
      command,
      "L'envoi d'images de posts",
      FfprobeImageProber.name,
    );
  }

  async onModuleInit() {
    await this.ffprobe.isAvailable();
  }

  async probe(path: string): Promise<ImageFormat> {
    if (!(await this.ffprobe.isAvailable())) {
      throw new ImageUnavailableError();
    }
    let output;
    try {
      output = await this.ffprobe.probe(path, IMAGE_FORMATS);
    } catch (error) {
      if (error instanceof ProbeFailedError) {
        throw new InvalidImageError();
      }
      throw error;
    }
    const stream = output.streams?.find((s) => s.codec_type === 'video');
    const format = stream?.codec_name ? CODECS[stream.codec_name] : undefined;
    if (!format || !stream?.width || !stream.height) {
      throw new InvalidImageError();
    }
    return format;
  }
}
