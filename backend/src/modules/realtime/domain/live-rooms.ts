// Port de la salle d'un live (ADR-029) : diffusion vers ses spectateurs, nombre de spectateurs
// et fermeture. Les cas d'usage du live n'en voient que ce port, pas la passerelle.

export const LIVE_ROOMS = Symbol('LIVE_ROOMS');

export interface LiveEvents {
  'live:message': unknown;
  'live:reaction': { liveId: string; emoji: string };
  'live:viewers': { liveId: string; viewerCount: number };
  'live:ended': { liveId: string };
}

export interface LiveRooms {
  // Envoie l'événement à tous les spectateurs présents dans la salle du live.
  toLive<E extends keyof LiveEvents>(
    liveId: string,
    event: E,
    data: LiveEvents[E],
  ): void;
  // Spectateurs présents : un compte connecté compte pour 1, même sur plusieurs appareils.
  viewerCount(liveId: string): number;
  // Retire tous les spectateurs : le live est terminé.
  close(liveId: string): void;
}
