import type { LiveRooms } from '../domain/live-rooms.js';

// Au plus une diffusion du compteur par seconde et par live (ADR-029, D4).
export const VIEWERS_BROADCAST_INTERVAL_MS = 1000;

const LIVE_ROOM_PREFIX = 'live:';

interface ThrottleState {
  lastSentAt: number;
  timer: ReturnType<typeof setTimeout> | null;
}

/**
 * Diffuse le nombre de spectateurs d'un live (`live:viewers`) à chaque entrée ou sortie de sa
 * salle. Le premier changement part aussitôt : une déconnexion propre baisse le compteur sans
 * attendre. Les suivants sont regroupés, et la valeur finale part toujours à la fin de
 * l'intervalle, pour ne pas inonder la salle.
 */
export class LiveViewersService {
  private readonly states = new Map<string, ThrottleState>();

  constructor(
    private readonly rooms: LiveRooms,
    private readonly intervalMs: number = VIEWERS_BROADCAST_INTERVAL_MS,
    private readonly now: () => number = Date.now,
  ) {}

  // Appelée par la passerelle à chaque changement de composition d'une salle.
  onRoomChanged(room: string): void {
    if (!room.startsWith(LIVE_ROOM_PREFIX)) {
      return;
    }
    const liveId = room.slice(LIVE_ROOM_PREFIX.length);
    const state = this.states.get(liveId) ?? { lastSentAt: 0, timer: null };
    this.states.set(liveId, state);
    if (state.timer) {
      return; // une diffusion est déjà prévue : elle enverra la valeur du moment
    }
    const wait = state.lastSentAt + this.intervalMs - this.now();
    if (wait <= 0) {
      this.broadcast(liveId, state);
    } else {
      state.timer = setTimeout(() => {
        state.timer = null;
        this.broadcast(liveId, state);
      }, wait);
    }
  }

  // Arrêt du serveur : plus aucune diffusion en attente.
  stop(): void {
    for (const state of this.states.values()) {
      if (state.timer) clearTimeout(state.timer);
    }
    this.states.clear();
  }

  private broadcast(liveId: string, state: ThrottleState): void {
    state.lastSentAt = this.now();
    const viewerCount = this.rooms.viewerCount(liveId);
    this.rooms.toLive(liveId, 'live:viewers', { liveId, viewerCount });
    // Salle vidée : inutile de garder l'état d'un live que plus personne ne regarde.
    if (viewerCount === 0 && !state.timer) {
      this.states.delete(liveId);
    }
  }
}
