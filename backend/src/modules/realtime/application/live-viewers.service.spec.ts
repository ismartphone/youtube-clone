import type { LiveRooms } from '../domain/live-rooms.js';
import { LiveViewersService } from './live-viewers.service.js';

class FakeRooms implements LiveRooms {
  count = 0;
  sent: unknown[] = [];
  toLive(_liveId: string, _event: string, data: unknown) {
    this.sent.push(data);
  }
  viewerCount() {
    return this.count;
  }
  close() {}
}

describe('LiveViewersService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('ignore les salles qui ne sont pas celles d’un live', () => {
    const rooms = new FakeRooms();
    const service = new LiveViewersService(rooms, 1000, () => Date.now());
    service.onRoomChanged('video:abc');
    service.onRoomChanged('conversation:abc');
    expect(rooms.sent).toEqual([]);
  });

  it('diffuse aussitôt le premier changement', () => {
    const rooms = new FakeRooms();
    const service = new LiveViewersService(rooms, 1000, () => Date.now());
    rooms.count = 1;
    service.onRoomChanged('live:l');
    expect(rooms.sent).toEqual([{ liveId: 'l', viewerCount: 1 }]);
  });

  it('regroupe les changements suivants et envoie la valeur finale à la fin de l’intervalle', () => {
    const rooms = new FakeRooms();
    const service = new LiveViewersService(rooms, 1000, () => Date.now());
    rooms.count = 1;
    service.onRoomChanged('live:l');
    rooms.count = 2;
    service.onRoomChanged('live:l');
    rooms.count = 3;
    service.onRoomChanged('live:l');
    expect(rooms.sent).toHaveLength(1);
    vi.advanceTimersByTime(1000);
    expect(rooms.sent).toEqual([
      { liveId: 'l', viewerCount: 1 },
      { liveId: 'l', viewerCount: 3 },
    ]);
  });

  it('n’envoie rien de plus si rien ne change après l’intervalle', () => {
    const rooms = new FakeRooms();
    const service = new LiveViewersService(rooms, 1000, () => Date.now());
    rooms.count = 1;
    service.onRoomChanged('live:l');
    vi.advanceTimersByTime(5000);
    expect(rooms.sent).toHaveLength(1);
  });

  it('diffuse de nouveau aussitôt après un intervalle sans changement', () => {
    const rooms = new FakeRooms();
    const service = new LiveViewersService(rooms, 1000, () => Date.now());
    rooms.count = 2;
    service.onRoomChanged('live:l');
    vi.advanceTimersByTime(2000);
    rooms.count = 1;
    service.onRoomChanged('live:l');
    expect(rooms.sent).toEqual([
      { liveId: 'l', viewerCount: 2 },
      { liveId: 'l', viewerCount: 1 },
    ]);
  });

  it('stop annule les diffusions en attente', () => {
    const rooms = new FakeRooms();
    const service = new LiveViewersService(rooms, 1000, () => Date.now());
    rooms.count = 1;
    service.onRoomChanged('live:l');
    service.onRoomChanged('live:l');
    service.stop();
    vi.advanceTimersByTime(5000);
    expect(rooms.sent).toHaveLength(1);
  });
});
