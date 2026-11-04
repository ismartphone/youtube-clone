// Publicités vidéo (ADR-021), indépendantes de Prisma et de NestJS. Une publicité n'est pas une
// vidéo du catalogue : ni vue comptée, ni page, ni place dans la recherche ou les listes.

export const AD_TITLE_MAX_LENGTH = 100;

// 200 Mo par fichier, comme les vidéos (ADR-015).
export const MAX_AD_UPLOAD_BYTES = 200 * 1024 * 1024;

export interface Ad {
  id: string;
  title: string;
  videoUrl: string;
  // Null tant que l'échelle HLS n'est pas prête : le MP4 est lu en attendant.
  hlsUrl: string | null;
  thumbnailUrl: string | null;
  durationSeconds: number;
  // Site de l'annonceur (« Visiter le site », bandeau sponsorisé). Null seulement pour une
  // publicité enregistrée avant ce champ.
  destinationUrl: string | null;
  createdAt: Date;
}

export interface NewAd {
  title: string;
  videoUrl: string;
  thumbnailUrl: string | null;
  durationSeconds: number;
  destinationUrl: string;
}

// Champs modifiables depuis le back-office ; un champ absent n'est pas modifié.
export interface AdChanges {
  title?: string;
  destinationUrl?: string;
}

export const AD_URL_MAX_LENGTH = 2048;

export class AdNotFoundError extends Error {
  constructor() {
    super('Publicité introuvable');
  }
}
