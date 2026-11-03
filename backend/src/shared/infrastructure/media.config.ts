import { basename, dirname, join } from 'node:path';

// Stockage local des fichiers générés par le serveur (ADR-002 : dossier local en développement,
// Cloudflare R2 au déploiement). Servi en statique sous MEDIA_URL_PREFIX par main.ts.

export const MEDIA_URL_PREFIX = '/media';

// Dossier racine, surchargeable par MEDIA_DIR. Par défaut : backend/storage/media (hors du dépôt).
export function mediaDirectory(): string {
  return process.env.MEDIA_DIR ?? join(process.cwd(), 'storage', 'media');
}

// Commande FFmpeg, surchargeable par FFMPEG_PATH (chemin complet de l'exécutable).
export function ffmpegCommand(): string {
  return process.env.FFMPEG_PATH ?? 'ffmpeg';
}

// Commande ffprobe, surchargeable par FFPROBE_PATH. ffprobe est livré avec FFmpeg : par défaut,
// à côté de l'exécutable indiqué dans FFMPEG_PATH.
export function ffprobeCommand(): string {
  if (process.env.FFPROBE_PATH) {
    return process.env.FFPROBE_PATH;
  }
  const ffmpeg = process.env.FFMPEG_PATH;
  return ffmpeg
    ? join(dirname(ffmpeg), basename(ffmpeg).replace(/ffmpeg/i, 'ffprobe'))
    : 'ffprobe';
}
