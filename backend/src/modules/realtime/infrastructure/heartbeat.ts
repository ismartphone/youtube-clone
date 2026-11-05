// Battement de la passerelle (ADR-029, étape 1) : le serveur envoie un ping à intervalle régulier
// à chaque connexion et coupe celle qui n'a pas répondu depuis le ping précédent. Il ne sert qu'aux
// coupures brutales (réseau perdu, application tuée) : une fermeture propre est vue aussitôt.
// Sans lui, une socket morte resterait dans sa salle, et donc dans le compteur de spectateurs,
// tant que le système ne la ferme pas.

// Le sous-ensemble de WebSocket (bibliothèque ws) dont le battement a besoin.
export interface HeartbeatClient {
  ping(): void;
  terminate(): void;
}

export const HEARTBEAT_INTERVAL_MS = 30_000;

export class Heartbeat {
  // Connexions pingées qui n'ont pas encore répondu.
  private readonly waiting = new Set<HeartbeatClient>();
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly clients: () => Iterable<HeartbeatClient>,
    private readonly intervalMs: number = HEARTBEAT_INTERVAL_MS,
  ) {}

  start(): void {
    if (this.timer) {
      return;
    }
    this.timer = setInterval(() => this.tick(), this.intervalMs);
    // Ne retient pas le processus à l'arrêt.
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.waiting.clear();
  }

  // La connexion a répondu (pong) : elle est vivante.
  alive(client: HeartbeatClient): void {
    this.waiting.delete(client);
  }

  // La connexion est fermée : plus rien à attendre d'elle.
  forget(client: HeartbeatClient): void {
    this.waiting.delete(client);
  }

  tick(): void {
    const current = new Set(this.clients());
    for (const client of current) {
      if (this.waiting.has(client)) {
        // Pas de réponse depuis le dernier ping : connexion morte, coupée (donc sortie des salles).
        this.waiting.delete(client);
        client.terminate();
        continue;
      }
      this.waiting.add(client);
      try {
        client.ping();
      } catch {
        client.terminate();
      }
    }
    // Les connexions disparues depuis ne sont plus attendues.
    for (const client of this.waiting) {
      if (!current.has(client)) this.waiting.delete(client);
    }
  }
}
