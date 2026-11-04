// Publicité vidéo (ADR-021), telle que renvoyée par GET /admin/ads.
export interface Ad {
  id: string;
  title: string;
  videoUrl: string;
  // Null tant que l'échelle HLS est en cours de génération : la publicité est lue en MP4.
  hlsUrl: string | null;
  thumbnailUrl: string | null;
  durationSeconds: number;
  createdAt: string;
}

export const AD_TITLE_MAX_LENGTH = 100;

// 200 Mo, la même limite que l'API.
export const MAX_AD_FILE_BYTES = 200 * 1024 * 1024;
