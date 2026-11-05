import type { WebSocket } from 'ws';
import { InMemoryRooms, liveRoom, videoRoom } from './in-memory-rooms.js';

// Socket factice : assez pour les salles, qui ne l'utilisent que comme clé et pour envoyer.
function socket(): WebSocket {
  return { readyState: 1, send: () => undefined } as unknown as WebSocket;
}

describe('InMemoryRooms — spectateurs d’un live', () => {
  it('compte un compte connecté pour 1, même sur plusieurs appareils', () => {
    const rooms = new InMemoryRooms();
    rooms.join(socket(), liveRoom('l'), 'camille');
    rooms.join(socket(), liveRoom('l'), 'camille');
    rooms.join(socket(), liveRoom('l'), 'lucas');
    expect(rooms.viewerCount('l')).toBe(2);
  });

  it('compte chaque visiteur non connecté pour 1', () => {
    const rooms = new InMemoryRooms();
    rooms.join(socket(), liveRoom('l'), null);
    rooms.join(socket(), liveRoom('l'), null);
    expect(rooms.viewerCount('l')).toBe(2);
  });

  it('baisse aussitôt sur un leave et sur une fermeture de connexion', () => {
    const rooms = new InMemoryRooms();
    const a = socket();
    const b = socket();
    rooms.join(a, liveRoom('l'), 'camille');
    rooms.join(b, liveRoom('l'), 'lucas');
    rooms.leave(a, liveRoom('l'));
    expect(rooms.viewerCount('l')).toBe(1);
    rooms.leaveAll(b);
    expect(rooms.viewerCount('l')).toBe(0);
  });

  it('un compte reste compté tant qu’un de ses appareils est dans la salle', () => {
    const rooms = new InMemoryRooms();
    const phone = socket();
    const tablet = socket();
    rooms.join(phone, liveRoom('l'), 'camille');
    rooms.join(tablet, liveRoom('l'), 'camille');
    rooms.leaveAll(phone);
    expect(rooms.viewerCount('l')).toBe(1);
  });

  it('ne mélange pas les salles de deux lives ni celle d’une vidéo', () => {
    const rooms = new InMemoryRooms();
    rooms.join(socket(), liveRoom('a'), 'camille');
    rooms.join(socket(), liveRoom('b'), 'lucas');
    rooms.join(socket(), videoRoom('a'), 'lucas');
    expect(rooms.viewerCount('a')).toBe(1);
    expect(rooms.viewerCount('b')).toBe(1);
  });

  it('close retire tous les spectateurs', () => {
    const rooms = new InMemoryRooms();
    rooms.join(socket(), liveRoom('l'), 'camille');
    rooms.join(socket(), liveRoom('l'), null);
    rooms.close('l');
    expect(rooms.viewerCount('l')).toBe(0);
  });

  it('prévient les écouteurs à chaque entrée et sortie, avec la salle concernée', () => {
    const rooms = new InMemoryRooms();
    const changes: string[] = [];
    rooms.onRoomChanged((room) => changes.push(room));
    const a = socket();
    rooms.join(a, liveRoom('l'), 'camille');
    rooms.leave(a, liveRoom('l'));
    expect(changes).toEqual([liveRoom('l'), liveRoom('l')]);
  });
});
