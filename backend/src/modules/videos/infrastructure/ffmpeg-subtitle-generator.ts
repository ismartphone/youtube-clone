import { Logger, OnModuleInit } from '@nestjs/common';
import { execFile } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { access, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { promisify } from 'node:util';
import { Ffprobe } from '../../../shared/infrastructure/ffprobe.js';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import {
  hasSpeech,
  srtToWebVtt,
  transcriptionTimeoutMs,
  type SubtitleGenerator,
} from '../domain/subtitles.js';

const run = promisify(execFile);

const SUBTITLES_FOLDER = 'subtitles';
const VIDEOS_PREFIX = `${MEDIA_URL_PREFIX}/videos/`;
const URL_PREFIX = `${MEDIA_URL_PREFIX}/${SUBTITLES_FOLDER}/`;
// Fichier généré : identifiant aléatoire, distinct de celui de la vidéo (ADR-017).
const GENERATED_URL = /^\/media\/subtitles\/([0-9a-f-]{36})\.vtt$/;
const FILTERS_CHECK_TIMEOUT_MS = 5_000;
// Whisper attend un son à 16 kHz ; il transcrit par tranches de 20 secondes.
const SAMPLE_RATE = 16_000;
const QUEUE_SECONDS = 20;

export interface FfmpegSubtitleConfig {
  ffmpeg: string;
  ffprobe: string;
  mediaDir: string;
  // Fichier du modèle whisper.cpp (ggml-base.bin), téléchargé hors du dépôt.
  model: string;
}

/**
 * Sous-titres transcrits par le filtre whisper de FFmpeg (ADR-017), en SRT converti en WebVTT.
 * FFmpeg sans ce filtre, ou modèle absent : le serveur démarre quand même, avec une erreur dans
 * les journaux, et les vidéos sont publiées sans sous-titres.
 */
export class FfmpegSubtitleGenerator
  implements SubtitleGenerator, OnModuleInit
{
  private readonly logger = new Logger(FfmpegSubtitleGenerator.name);
  private readonly ffprobe: Ffprobe;
  private available: Promise<boolean> | undefined;

  constructor(private readonly config: FfmpegSubtitleConfig) {
    this.ffprobe = new Ffprobe(
      config.ffprobe,
      'La génération des sous-titres',
      FfmpegSubtitleGenerator.name,
    );
  }

  async onModuleInit() {
    await this.isAvailable();
  }

  // Vérifiée une seule fois, au démarrage (ou au premier appel hors serveur, dans la commande).
  isAvailable(): Promise<boolean> {
    this.available ??= this.check().then(
      () => true,
      (error: Error) => {
        this.logger.error(
          `${error.message} Les vidéos sont publiées sans sous-titres ; le reste de l'API fonctionne.`,
        );
        return false;
      },
    );
    return this.available;
  }

  isLocal(videoUrl: string): boolean {
    return videoUrl.startsWith(VIDEOS_PREFIX);
  }

  async generate(videoUrl: string, durationSeconds: number): Promise<string> {
    const source = this.localPath(videoUrl);
    const timeoutMs = transcriptionTimeoutMs(durationSeconds);
    if (!(await this.isAvailable())) {
      throw new Error(
        'transcription indisponible (filtre whisper ou modèle absent)',
      );
    }
    const probe = await this.ffprobe.probe(source, 'mov');
    if (!probe.streams?.some((s) => s.codec_type === 'audio')) {
      throw new Error('aucune piste audio à transcrire');
    }

    const id = randomUUID();
    const folder = join(this.config.mediaDir, SUBTITLES_FOLDER);
    await mkdir(folder, { recursive: true });
    const srt = join(folder, `${id}.srt`);
    const vtt = join(folder, `${id}.vtt`);
    const whisper = [
      `model=${filterValue(resolve(this.config.model))}`,
      'language=auto',
      `queue=${QUEUE_SECONDS}`,
      'format=srt',
      `destination=${filterValue(resolve(srt))}`,
    ].join(':');
    try {
      await run(
        this.config.ffmpeg,
        [
          '-v',
          'error',
          '-i',
          source,
          '-vn',
          '-af',
          `aresample=${SAMPLE_RATE},whisper=${whisper}`,
          '-f',
          'null',
          '-',
        ],
        { timeout: timeoutMs, maxBuffer: 10 * 1024 * 1024 },
      );
      const transcript = await readFile(srt, 'utf8');
      const content = hasSpeech(transcript, durationSeconds)
        ? srtToWebVtt(transcript)
        : null;
      if (content === null) {
        throw new Error('aucune parole détectée');
      }
      await writeFile(vtt, content, 'utf8');
      return `${URL_PREFIX}${id}.vtt`;
    } catch (error) {
      await rm(vtt, { force: true });
      const failure = error as {
        killed?: boolean;
        stderr?: string;
        message?: string;
      };
      if (failure.killed) {
        throw new Error(
          `transcription abandonnée au bout de ${Math.round(timeoutMs / 1000)} s`,
        );
      }
      throw new Error(
        failure.stderr?.trim().split('\n').at(-1) || failure.message,
      );
    } finally {
      await rm(srt, { force: true });
    }
  }

  async remove(subtitleUrl: string): Promise<void> {
    const id = GENERATED_URL.exec(subtitleUrl)?.[1];
    if (id) {
      await rm(join(this.config.mediaDir, SUBTITLES_FOLDER, `${id}.vtt`), {
        force: true,
      });
    }
  }

  private async check(): Promise<void> {
    let filters: string;
    try {
      ({ stdout: filters } = await run(
        this.config.ffmpeg,
        ['-hide_banner', '-filters'],
        { timeout: FILTERS_CHECK_TIMEOUT_MS },
      ));
    } catch {
      throw new Error(
        `FFmpeg est introuvable (commande « ${this.config.ffmpeg} »).`,
      );
    }
    if (!/^\s*\S*\s+whisper\s/m.test(filters)) {
      throw new Error(
        "Le FFmpeg installé n'a pas le filtre whisper : installez une version compilée avec --enable-whisper.",
      );
    }
    try {
      await access(this.config.model);
    } catch {
      throw new Error(
        `Modèle whisper introuvable (${this.config.model}) : téléchargez-le, voir backend/README.md.`,
      );
    }
  }

  // Le chemin ne peut pas sortir du dossier de stockage.
  private localPath(videoUrl: string): string {
    if (!this.isLocal(videoUrl)) {
      throw new Error('vidéo externe : pas de sous-titres');
    }
    const root = resolve(this.config.mediaDir);
    const file = resolve(root, `.${videoUrl.slice(MEDIA_URL_PREFIX.length)}`);
    if (!file.startsWith(root + sep)) {
      throw new Error('chemin de fichier invalide');
    }
    return file;
  }
}

// Chemin passé en option d'un filtre FFmpeg : barres obliques, puis deux niveaux d'échappement,
// celui de la valeur d'option (« : » et « ' ») et celui de la description du graphe de filtres.
function filterValue(path: string): string {
  return path
    .replace(/\\/g, '/')
    .replace(/[:']/g, (c) => `\\${c}`)
    .replace(/[\\',;[\]]/g, (c) => `\\${c}`);
}
