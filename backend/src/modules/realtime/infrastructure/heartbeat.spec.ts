import { Heartbeat, type HeartbeatClient } from './heartbeat.js';

function client() {
  return {
    pings: 0,
    terminated: false,
    ping() {
      this.pings += 1;
    },
    terminate() {
      this.terminated = true;
    },
  } satisfies HeartbeatClient & { pings: number; terminated: boolean };
}

describe('Heartbeat', () => {
  it('envoie un ping à chaque connexion à chaque battement', () => {
    const a = client();
    const heartbeat = new Heartbeat(() => [a]);
    heartbeat.tick();
    expect(a.pings).toBe(1);
    expect(a.terminated).toBe(false);
  });

  it('garde la connexion qui répond entre deux battements', () => {
    const a = client();
    const heartbeat = new Heartbeat(() => [a]);
    heartbeat.tick();
    heartbeat.alive(a);
    heartbeat.tick();
    heartbeat.alive(a);
    heartbeat.tick();
    expect(a.terminated).toBe(false);
    expect(a.pings).toBe(3);
  });

  it('coupe la connexion qui n’a pas répondu au ping précédent', () => {
    const a = client();
    const heartbeat = new Heartbeat(() => [a]);
    heartbeat.tick();
    heartbeat.tick();
    expect(a.terminated).toBe(true);
  });

  it('ne coupe pas une connexion saine à cause d’une autre', () => {
    const live = client();
    const dead = client();
    const heartbeat = new Heartbeat(() => [live, dead]);
    heartbeat.tick();
    heartbeat.alive(live);
    heartbeat.tick();
    expect(live.terminated).toBe(false);
    expect(dead.terminated).toBe(true);
  });

  it('n’attend plus une connexion fermée', () => {
    const a = client();
    let clients: HeartbeatClient[] = [a];
    const heartbeat = new Heartbeat(() => clients);
    heartbeat.tick();
    clients = [];
    heartbeat.forget(a);
    heartbeat.tick();
    expect(a.terminated).toBe(false);
  });

  it('bat à intervalle régulier une fois démarré, et s’arrête', () => {
    vi.useFakeTimers();
    try {
      const a = client();
      const heartbeat = new Heartbeat(() => [a], 30_000);
      heartbeat.start();
      vi.advanceTimersByTime(30_000);
      expect(a.pings).toBe(1);
      heartbeat.stop();
      vi.advanceTimersByTime(120_000);
      expect(a.pings).toBe(1);
    } finally {
      vi.useRealTimers();
    }
  });
});
