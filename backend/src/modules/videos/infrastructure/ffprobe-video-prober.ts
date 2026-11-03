import { OnModuleInit } from '@nestjs/common';
import {
  Ffprobe,
  ProbeFailedError,
} from '../../../shared/infrastructure/ffprobe.js';
import {
  InvalidVideoFileError,
  UploadUnavailableError,
  type ProbedVideo,
  type VideoProber,
} from '../domain/video-upload.js';

/**
 * Analyse d'une vidéo envoyée, avec ffprobe (ADR-011, ADR-015). Le fichier n'est lu que comme
 * MP4 (démultiplexeur « mov », qui couvre MP4 et MOV).
 * ffprobe absent : le serveur démarre quand même, et seul l'envoi est refusé.
 */
export class FfprobeVideoProber implements VideoProber, OnModuleInit {
  private readonly ffprobe: Ffprobe;

  constructor(command: string) {
    this.ffprobe = new Ffprobe(
      command,
      "L'envoi de vidéos depuis l'application",
      FfprobeVideoProber.name,
    );
  }

  async onModuleInit() {
    await this.ffprobe.isAvailable();
  }

  async probe(path: string): Promise<ProbedVideo> {
    if (!(await this.ffprobe.isAvailable())) {
      throw new UploadUnavailableError();
    }
    let output;
    try {
      output = await this.ffprobe.probe(path, 'mov');
    } catch (error) {
      if (error instanceof ProbeFailedError) {
        throw new InvalidVideoFileError(
          "Ce fichier n'est pas une vidéo lisible. Envoyez une vidéo au format MP4.",
        );
      }
      throw error;
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
