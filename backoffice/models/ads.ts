// Publicité vidéo (ADR-021), telle que renvoyée par GET /admin/ads.
export interface Ad {
  id: string;
  title: string;
  videoUrl: string;
  // Null tant que l'échelle HLS est en cours de génération : la publicité est lue en MP4.
  hlsUrl: string | null;
  thumbnailUrl: string | null;
  durationSeconds: number;
  // Site de l'annonceur ; null seulement pour une publicité enregistrée avant ce champ.
  destinationUrl: string | null;
  // Carte du bandeau sponsorisé. Nom et description : null seulement pour une publicité
  // enregistrée avant ces champs. Sans logo, l'application affiche l'initiale de l'annonceur.
  advertiserName: string | null;
  description: string | null;
  logoUrl: string | null;
  // Libellé du bouton principal, « Visiter le site » par défaut.
  ctaLabel: string;
  createdAt: string;
}

/** Champs de la carte, envoyés à l'import et à la modification. */
export interface AdFields {
  title: string;
  destinationUrl: string;
  advertiserName: string;
  description: string;
  // Absent : le serveur garde « Visiter le site ».
  ctaLabel?: string;
}

// Mêmes limites que l'API.
export const AD_TITLE_MAX_LENGTH = 100;
export const AD_ADVERTISER_MAX_LENGTH = 60;
export const AD_DESCRIPTION_MAX_LENGTH = 150;
export const AD_CTA_MAX_LENGTH = 25;
export const DEFAULT_AD_CTA = "Visiter le site";
export const MAX_AD_FILE_BYTES = 200 * 1024 * 1024;
export const MAX_AD_LOGO_BYTES = 5 * 1024 * 1024;
