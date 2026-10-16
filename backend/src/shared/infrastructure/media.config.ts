import { join } from 'node:path';

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
