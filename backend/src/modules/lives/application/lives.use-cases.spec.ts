import type { LiveRooms } from '../../realtime/domain/live-rooms.js';
import {
  ChannelRequiredError,
  ChatRateLimiter,
  ChatTooFastError,
  ReactionRateLimiter,
  ReactionsTooFastError,
  LiveAlreadyRunningError,
  LiveEndedError,
  NotEnoughFollowersError,
  VideoNotEligibleError,
  type LiveChatMessage,
  type LiveRecord,
  type StreamCandidate,
} from '../domain/live.js';
import type { LivesRepository } from '../domain/ports.js';
import { LivesUseCases, NotLiveOwnerError } from './lives.use-cases.js';

const CHANNEL = {
  id: 'chaine',
  name: 'Studio Libre',
  handle: 'studiolibre',
  avatarUrl: null,
  ownerId: 'diffuseur',
};

function liveRecord(overrides: Partial<LiveRecord> = {}): LiveRecord {
  return {
    id: 'live',
    title: 'Mon live',
    status: 'LIVE',
    startedAt: new Date('2026-11-05T10:00:00Z'),
    endedAt: null,
    channel: CHANNEL,
    video: {
      id: 'video',
      videoUrl: '/media/videos/a.mp4',
      hlsUrl: null,
      thumbnailUrl: '/media/thumbnails/a.jpg',
      durationSeconds: 52,
      format: 'LONG',
      channelId: 'chaine',
      visibility: 'PUBLIC',
      isPremium: false,
    },
    ...overrides,
  };
}

// Dépôt en mémoire : assez pour exercer les règles des cas d'usage.
class FakeRepository implements LivesRepository {
  followers = 50;
  candidate: StreamCandidate | null = {
    channelId: 'chaine',
    visibility: 'PUBLIC',
    isPremium: false,
  };
  hasChannel = true;
  lives = new Map<string, LiveRecord>();
  messages: LiveChatMessage[] = [];

  findOwnChannelId() {
    return Promise.resolve(this.hasChannel ? 'chaine' : null);
  }
  countFollowers() {
    return Promise.resolve(this.followers);
  }
  findStreamCandidate() {
    return Promise.resolve(this.candidate);
  }
  createIfNoneRunning(_channelId: string, title: string) {
    const running = [...this.lives.values()].some((l) => l.status === 'LIVE');
    if (running) return Promise.resolve(null);
    const live = liveRecord({ title });
    this.lives.set(live.id, live);
    return Promise.resolve(live);
  }
  find(liveId: string) {
    return Promise.resolve(this.lives.get(liveId) ?? null);
  }
  listRunning() {
    return Promise.resolve(
      [...this.lives.values()].filter((l) => l.status === 'LIVE'),
    );
  }
  end(liveId: string, at: Date) {
    const live = this.lives.get(liveId);
    if (!live || live.status === 'ENDED') return Promise.resolve(false);
    this.lives.set(liveId, { ...live, status: 'ENDED', endedAt: at });
    return Promise.resolve(true);
  }
  listMessages() {
    return Promise.resolve(this.messages);
  }
  addMessage(liveId: string, userId: string, content: string) {
    const message: LiveChatMessage = {
      id: `m${this.messages.length}`,
      liveId,
      kind: 'CHAT',
      content,
      createdAt: new Date(),
      author: { id: userId, displayName: 'Camille', avatarUrl: null },
    };
    this.messages.unshift(message);
    return Promise.resolve(message);
  }
  findAuthor() {
    return Promise.resolve(null);
  }
}

class FakeRooms implements LiveRooms {
  sent: { liveId: string; event: string; data: unknown }[] = [];
  closed: string[] = [];
  viewers = 3;
  toLive(liveId: string, event: string, data: unknown) {
    this.sent.push({ liveId, event, data });
  }
  viewerCount() {
    return this.viewers;
  }
  close(liveId: string) {
    this.closed.push(liveId);
  }
}

function setup() {
  const repository = new FakeRepository();
  const rooms = new FakeRooms();
  const useCases = new LivesUseCases(
    repository,
    rooms,
    new ChatRateLimiter(1000, () => 10_000),
  );
  return { repository, rooms, useCases };
}

describe('LivesUseCases.start', () => {
  it('démarre un live avec son nombre de spectateurs', async () => {
    const { useCases } = setup();
    const view = await useCases.start('diffuseur', '  Mon live  ', 'video');
    expect(view.title).toBe('Mon live');
    expect(view.status).toBe('LIVE');
    expect(view.viewerCount).toBe(3);
  });

  it('refuse un utilisateur sans chaîne', async () => {
    const { repository, useCases } = setup();
    repository.hasChannel = false;
    await expect(useCases.start('u', 'Titre', 'video')).rejects.toBeInstanceOf(
      ChannelRequiredError,
    );
  });

  it('refuse une chaîne de moins de 50 abonnés, côté serveur', async () => {
    const { repository, useCases } = setup();
    repository.followers = 49;
    await expect(
      useCases.start('diffuseur', 'Titre', 'video'),
    ).rejects.toBeInstanceOf(NotEnoughFollowersError);
  });

  it('accepte exactement 50 abonnés', async () => {
    const { repository, useCases } = setup();
    repository.followers = 50;
    await expect(
      useCases.start('diffuseur', 'Titre', 'video'),
    ).resolves.toBeDefined();
  });

  it("refuse une vidéo qui n'est pas admissible comme flux", async () => {
    const { repository, useCases } = setup();
    repository.candidate = {
      channelId: 'chaine',
      visibility: 'PRIVATE',
      isPremium: false,
    };
    await expect(
      useCases.start('diffuseur', 'Titre', 'video'),
    ).rejects.toBeInstanceOf(VideoNotEligibleError);
    repository.candidate = null;
    await expect(
      useCases.start('diffuseur', 'Titre', 'video'),
    ).rejects.toBeInstanceOf(VideoNotEligibleError);
  });

  it('refuse un second live en cours sur la même chaîne', async () => {
    const { useCases } = setup();
    await useCases.start('diffuseur', 'Premier', 'video');
    await expect(
      useCases.start('diffuseur', 'Second', 'video'),
    ).rejects.toBeInstanceOf(LiveAlreadyRunningError);
  });
});

describe('LivesUseCases.end', () => {
  it('termine le live, prévient la salle puis la ferme', async () => {
    const { rooms, useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    const view = await useCases.end('diffuseur', id);
    expect(view?.status).toBe('ENDED');
    expect(view?.viewerCount).toBe(0);
    expect(rooms.sent).toContainEqual({
      liveId: id,
      event: 'live:ended',
      data: { liveId: id },
    });
    expect(rooms.closed).toEqual([id]);
  });

  it('refuse un autre que le diffuseur', async () => {
    const { useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    await expect(useCases.end('intrus', id)).rejects.toBeInstanceOf(
      NotLiveOwnerError,
    );
  });

  it('ne prévient la salle qu’une fois si le live est déjà terminé', async () => {
    const { rooms, useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    await useCases.end('diffuseur', id);
    await useCases.end('diffuseur', id);
    expect(rooms.sent.filter((s) => s.event === 'live:ended')).toHaveLength(1);
  });

  it('renvoie null pour un live inexistant', async () => {
    const { useCases } = setup();
    await expect(useCases.end('diffuseur', 'inconnu')).resolves.toBeNull();
  });
});

describe('fin paresseuse du live (D1)', () => {
  it('termine le live quand sa vidéo est devenue privée, à la lecture de son état', async () => {
    const { repository, rooms, useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    const live = repository.lives.get(id)!;
    repository.lives.set(id, {
      ...live,
      video: { ...live.video!, visibility: 'PRIVATE' },
    });
    expect((await useCases.get(id))?.status).toBe('ENDED');
    expect(rooms.closed).toEqual([id]);
    await expect(useCases.listRunning()).resolves.toEqual([]);
  });

  it('termine le live quand sa vidéo a été supprimée', async () => {
    const { repository, useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    repository.lives.set(id, { ...repository.lives.get(id)!, video: null });
    expect((await useCases.get(id))?.status).toBe('ENDED');
  });
});

describe('LivesUseCases.listRunning', () => {
  it('liste les lives en cours avec leurs spectateurs', async () => {
    const { useCases } = setup();
    await useCases.start('diffuseur', 'Titre', 'video');
    const items = await useCases.listRunning();
    expect(items).toHaveLength(1);
    expect(items[0]?.viewerCount).toBe(3);
  });
});

describe('LivesUseCases.send', () => {
  it('enregistre le message et le diffuse à la salle', async () => {
    const { rooms, useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    const message = await useCases.send('camille', id, '  Bonjour  ');
    expect(message?.content).toBe('Bonjour');
    expect(rooms.sent).toContainEqual({
      liveId: id,
      event: 'live:message',
      data: message,
    });
  });

  it('refuse un second message dans la seconde', async () => {
    const { useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    await useCases.send('camille', id, 'Un');
    await expect(useCases.send('camille', id, 'Deux')).rejects.toBeInstanceOf(
      ChatTooFastError,
    );
  });

  it('refuse d’écrire dans un live terminé', async () => {
    const { useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    await useCases.end('diffuseur', id);
    await expect(useCases.send('camille', id, 'Salut')).rejects.toBeInstanceOf(
      LiveEndedError,
    );
  });

  it('renvoie null pour un live inexistant', async () => {
    const { useCases } = setup();
    await expect(
      useCases.send('camille', 'inconnu', 'Salut'),
    ).resolves.toBeNull();
  });
});

describe('LivesUseCases.react', () => {
  it('diffuse la réaction à la salle sans rien stocker', async () => {
    const { repository, rooms, useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    await expect(useCases.react('camille', id, '🔥')).resolves.toBe(true);
    expect(rooms.sent).toContainEqual({
      liveId: id,
      event: 'live:reaction',
      data: { liveId: id, emoji: '🔥' },
    });
    expect(repository.messages).toHaveLength(0);
  });

  it('refuse au-delà de quatre réactions par seconde, par compte', async () => {
    const { rooms, useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    for (let i = 0; i < 4; i++) await useCases.react('camille', id, '👍');
    await expect(useCases.react('camille', id, '👍')).rejects.toBeInstanceOf(
      ReactionsTooFastError,
    );
    // Un autre compte n'est pas touché.
    await expect(useCases.react('lea', id, '👍')).resolves.toBe(true);
    expect(rooms.sent.filter((e) => e.event === 'live:reaction')).toHaveLength(
      5,
    );
  });

  it('refuse de réagir dans un live terminé', async () => {
    const { useCases } = setup();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    await useCases.end('diffuseur', id);
    await expect(useCases.react('camille', id, '😂')).rejects.toBeInstanceOf(
      LiveEndedError,
    );
  });

  it('renvoie null pour un live inexistant', async () => {
    const { useCases } = setup();
    await expect(
      useCases.react('camille', 'inconnu', '😂'),
    ).resolves.toBeNull();
  });
});

describe('ReactionRateLimiter', () => {
  it('libère la fenêtre une fois la seconde écoulée', () => {
    let now = 0;
    const limiter = new ReactionRateLimiter(2, 1000, () => now);
    expect(limiter.tryAcquire('a')).toBe(true);
    expect(limiter.tryAcquire('a')).toBe(true);
    expect(limiter.tryAcquire('a')).toBe(false);
    now = 1000;
    expect(limiter.tryAcquire('a')).toBe(true);
  });
});

describe('LivesUseCases.listMessages', () => {
  it("renvoie null pour un live inexistant, l'historique sinon", async () => {
    const { useCases } = setup();
    await expect(useCases.listMessages('inconnu')).resolves.toBeNull();
    const { id } = await useCases.start('diffuseur', 'Titre', 'video');
    await expect(useCases.listMessages(id)).resolves.toEqual([]);
  });
});
