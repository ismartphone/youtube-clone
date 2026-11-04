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
  // Carte du bandeau sponsorisé. Nom et description : null seulement pour une publicité
  // enregistrée avant ces champs ; logo facultatif (sinon l'initiale de l'annonceur).
  advertiserName: string | null;
  description: string | null;
  logoUrl: string | null;
  // Libellé du bouton principal, repris par la bande pendant l'annonce.
  ctaLabel: string;
  createdAt: Date;
}

// Champs saisis à l'import, et modifiables ensuite depuis le back-office.
export interface AdFields {
  title: string;
  destinationUrl: string;
  advertiserName: string;
  description: string;
  // Absent : « Visiter le site ».
  ctaLabel?: string;
}

export interface NewAd extends AdFields {
  videoUrl: string;
  thumbnailUrl: string | null;
  durationSeconds: number;
}

// Champs modifiables depuis le back-office ; un champ absent n'est pas modifié.
export type AdChanges = Partial<AdFields>;

export const AD_URL_MAX_LENGTH = 2048;
export const AD_ADVERTISER_MAX_LENGTH = 60;
export const AD_DESCRIPTION_MAX_LENGTH = 150;
export const AD_CTA_MAX_LENGTH = 25;
export const DEFAULT_AD_CTA = 'Visiter le site';

export class AdNotFoundError extends Error {
  constructor() {
    super('Publicité introuvable');
  }
}
