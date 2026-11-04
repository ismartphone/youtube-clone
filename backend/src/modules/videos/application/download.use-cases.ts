import type { PremiumAccess } from '../domain/premium-access.js';
import { canSee } from '../domain/video.js';
import type { VideoFileStore } from '../domain/video-upload.js';
import type { VideoRepository } from '../domain/video.repository.js';

// Téléchargements pour la lecture hors ligne (ADR-019, partie 2) : réservés aux abonnés Premium,
// pour les vidéos hébergées chez nous. Le serveur remet l'adresse du fichier ; l'application ne
// télécharge jamais sans cette réponse. Le fichier lui-même reste servi en statique sous /media,
// sans protection (même limite que l'ADR-017).

// Vidéo hébergée chez nous : seule celle-ci se télécharge. Une vidéo externe n'est pas la nôtre.
export function isDownloadable(videoUrl: string): boolean {
  return videoUrl.startsWith('/media/videos/');
}

export class PremiumRequiredError extends Error {
  constructor() {
    super('Le téléchargement est réservé aux membres Premium');
  }
}

export class NotDownloadableError extends Error {
  constructor() {
    super("Cette vidéo n'est pas téléchargeable");
  }
}

export interface DownloadGrant {
  fileUrl: string;
  subtitleUrl: string | null;
  // Taille du fichier en octets, pour vérifier l'espace libre avant de télécharger ; null si
  // elle n'a pas pu être lue.
  sizeBytes: number | null;
  // Fin de l'abonnement : au-delà, la lecture hors ligne est refusée.
  expiresAt: Date;
}

export class AuthorizeDownloadUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly premium: PremiumAccess,
    private readonly files: VideoFileStore,
  ) {}

  /**
   * Renvoie null si la vidéo n'existe pas pour l'utilisateur (privée d'un autre, ADR-016). Lève
   * PremiumRequiredError sans abonnement en cours, NotDownloadableError pour une vidéo externe.
   */
  async execute(
    userId: string,
    videoId: string,
  ): Promise<DownloadGrant | null> {
    const expiresAt = await this.premium.activeUntil(userId);
    const video = await this.videos.findById(videoId);
    if (!video || !canSee(video, { isPremium: expiresAt !== null, userId })) {
      return null;
    }
    if (!expiresAt) {
      throw new PremiumRequiredError();
    }
    if (!isDownloadable(video.videoUrl)) {
      throw new NotDownloadableError();
    }
    return {
      fileUrl: video.videoUrl,
      subtitleUrl: video.subtitleUrl,
      sizeBytes: await this.files.size(video.videoUrl),
      expiresAt,
    };
  }
}

export interface DownloadCheck {
  // Fin de l'abonnement, ou null s'il n'y en a plus : tout est alors à supprimer.
  expiresAt: Date | null;
  // Vidéos téléchargées qui restent lisibles ; les autres sont à supprimer du téléphone.
  accessible: string[];
}

/**
 * Revalidation, à chaque connexion, des vidéos téléchargées (décision 10) : une vidéo devenue
 * privée ou supprimée, ou un abonnement terminé, ne reste pas lisible hors ligne.
 */
export class CheckDownloadsUseCase {
  constructor(
    private readonly videos: VideoRepository,
    private readonly premium: PremiumAccess,
  ) {}

  async execute(userId: string, videoIds: string[]): Promise<DownloadCheck> {
    const expiresAt = await this.premium.activeUntil(userId);
    if (!expiresAt) {
      return { expiresAt: null, accessible: [] };
    }
    const viewer = { isPremium: true, userId };
    const accessible: string[] = [];
    for (const id of new Set(videoIds)) {
      const video = await this.videos.findById(id);
      if (video && canSee(video, viewer) && isDownloadable(video.videoUrl)) {
        accessible.push(id);
      }
    }
    return { expiresAt, accessible };
  }
}
