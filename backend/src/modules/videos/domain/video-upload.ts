import type { VideoFormat } from './video.js';

// Publication d'une vidéo envoyée depuis l'application mobile (ADR-015). Le fichier vient du
// client : il n'est cru sur rien, ni son nom, ni son extension, ni le type annoncé. ffprobe
// l'analyse, puis le serveur le range sous un nom qu'il génère lui-même.

// 200 Mo par vidéo (ADR-015).
export const MAX_UPLOAD_BYTES = 200 * 1024 * 1024;

// Short : vidéo verticale de 60 secondes au plus. La demi-seconde de marge couvre l'arrondi
// des enregistreurs, qui produisent souvent 60,02 s pour « une minute ».
export const SHORT_MAX_SECONDS = 60;
const SHORT_TOLERANCE_SECONDS = 0.5;

// Ce que ffprobe a lu du fichier, orientation d'affichage comprise (rotation appliquée).
export interface ProbedVideo {
  durationSeconds: number;
  width: number;
  height: number;
}

export const VIDEO_PROBER = Symbol('VIDEO_PROBER');
export const VIDEO_FILE_STORE = Symbol('VIDEO_FILE_STORE');
export const CHANNEL_OWNERSHIP = Symbol('CHANNEL_OWNERSHIP');

export interface VideoProber {
  /**
   * Analyse le fichier local [path]. Lève UploadUnavailableError si ffprobe est absent,
   * InvalidVideoFileError si le fichier n'est pas une vidéo MP4 lisible.
   */
  probe(path: string): Promise<ProbedVideo>;
}

export interface VideoFileStore {
  // Range le fichier temporaire [path] dans le stockage, sous un nom généré, et renvoie son URL.
  save(path: string): Promise<string>;
  // Supprime un fichier rangé par save ; sans effet pour toute autre URL.
  remove(videoUrl: string): Promise<void>;
}

export interface ChannelOwnership {
  // Chaîne dont l'utilisateur est propriétaire, ou null s'il n'en a pas.
  channelIdOf(userId: string): Promise<string | null>;
}

// Métadonnées saisies sur l'écran de publication.
export interface UploadDetails {
  title: string;
  description: string | null;
  categoryId: string;
  format: VideoFormat;
}

// Refus d'un fichier qui ne convient pas au format choisi : message destiné au créateur.
export function formatProblem(
  format: VideoFormat,
  video: ProbedVideo,
): string | null {
  if (format !== 'SHORT') {
    return null;
  }
  if (video.height <= video.width) {
    return 'Un Short doit être une vidéo verticale.';
  }
  if (video.durationSeconds > SHORT_MAX_SECONDS + SHORT_TOLERANCE_SECONDS) {
    return `Un Short dure ${SHORT_MAX_SECONDS} secondes au plus.`;
  }
  return null;
}

export class NoChannelError extends Error {
  constructor() {
    super('Créez une chaîne pour publier des vidéos.');
  }
}

export class InvalidVideoFileError extends Error {}

export class UploadUnavailableError extends Error {
  constructor() {
    super(
      "L'envoi de vidéos est momentanément indisponible sur le serveur. Réessayez plus tard.",
    );
  }
}
