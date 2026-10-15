export const VIEW_COUNTER = Symbol('VIEW_COUNTER');

// Port du compteur de vues. Les vues passent par un tampon avant d'être écrites en base (ADR-004).
export interface ViewCounter {
  increment(videoId: string): Promise<void>;
  // Vues enregistrées mais pas encore reportées en base, par identifiant de vidéo.
  pendingCounts(videoIds: string[]): Promise<Map<string, number>>;
  // Total des vues pas encore reportées en base, toutes vidéos confondues.
  pendingTotal(): Promise<number>;
}
