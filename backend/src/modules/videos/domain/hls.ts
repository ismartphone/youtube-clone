// Diffusion HLS multi-qualités (ADR-017) : une échelle générée hors ligne par FFmpeg, une fois
// par vidéo hébergée chez nous, sous /media/hls/<identifiant aléatoire>/. Le MP4 reste le repli.

export const HLS_GENERATOR = Symbol('HLS_GENERATOR');
export const HLS_QUEUE = Symbol('HLS_QUEUE');

// Qualités proposées, désignées par leur petit côté (hauteur d'une vidéo horizontale, largeur
// d'un Short vertical), avec leur débit vidéo cible en kbit/s. Plafond à 720p : le décodeur
// logiciel de l'émulateur, où se fait la démonstration, plante sur le 1080p (ADR-017) ; le
// décodeur matériel d'un vrai appareil n'a pas cette limite.
export const HLS_LADDER = [
  { size: 720, videoKbps: 2800 },
  { size: 480, videoKbps: 1400 },
  { size: 360, videoKbps: 800 },
] as const;

export interface HlsRung {
  size: number;
  videoKbps: number;
}

/**
 * Qualités à produire pour une source de [width] x [height] (dimensions affichées) : celles dont
 * le petit côté ne dépasse pas celui de la source, car on n'agrandit jamais une vidéo. Une source
 * plus petite que 360 garde une seule qualité, à sa propre taille.
 */
export function hlsRungs(width: number, height: number): HlsRung[] {
  const shortSide = Math.min(width, height);
  const rungs = HLS_LADDER.filter((rung) => rung.size <= shortSide);
  if (rungs.length > 0) {
    return rungs.map((rung) => ({ ...rung }));
  }
  // Petit côté pair, comme l'exige l'encodeur H.264.
  return [{ size: shortSide - (shortSide % 2), videoKbps: 600 }];
}

export interface HlsGenerator {
  // Vrai si la vidéo est hébergée chez nous (/media/videos/...) : seule celle-ci a un HLS.
  isLocal(videoUrl: string): boolean;
  /**
   * Génère l'échelle HLS de la vidéo [videoUrl] et renvoie l'URL relative du manifeste principal.
   * Lève une erreur si FFmpeg est absent ou si l'encodage échoue (rien n'est laissé sur le disque).
   */
  generate(videoUrl: string): Promise<string>;
  // Supprime le dossier d'un HLS généré ; sans effet pour toute autre URL.
  remove(hlsUrl: string): Promise<void>;
}

// File de génération en tâche de fond : la publication n'attend jamais le HLS (ADR-017).
export interface HlsQueue {
  enqueue(videoId: string): void;
}
