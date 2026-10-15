// Compteurs tamponnés (ADR-004) : chaque variation s'ajoute dans Redis, puis les totaux
// sont reportés en base par lot. On n'écrit pas en base à chaque vue ou « j'aime ».

export const VIDEO_COUNTERS = Symbol('VIDEO_COUNTERS');

export interface BufferedCounter {
  // delta négatif pour retirer (un « j'aime » annulé).
  increment(videoId: string, delta?: number): Promise<void>;
  // Variations enregistrées mais pas encore reportées en base, par identifiant de vidéo.
  pendingCounts(videoIds: string[]): Promise<Map<string, number>>;
  // Somme des variations en attente, toutes vidéos confondues.
  pendingTotal(): Promise<number>;
}

export interface VideoCounters {
  views: BufferedCounter;
  likes: BufferedCounter;
}
