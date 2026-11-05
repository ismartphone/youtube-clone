import {
  ChatRateLimiter,
  canStreamVideo,
  toLiveView,
  type LiveRecord,
} from './live.js';

describe('canStreamVideo', () => {
  const video = {
    channelId: 'chaine',
    visibility: 'PUBLIC' as const,
    isPremium: false,
  };

  it('accepte une vidéo publique, non Premium, de la chaîne du diffuseur', () => {
    expect(canStreamVideo(video, 'chaine')).toBe(true);
  });

  it('refuse une vidéo inexistante', () => {
    expect(canStreamVideo(null, 'chaine')).toBe(false);
  });

  it("refuse la vidéo d'une autre chaîne", () => {
    expect(canStreamVideo(video, 'autre')).toBe(false);
  });

  it('refuse une vidéo privée ou non répertoriée (ADR-016)', () => {
    expect(canStreamVideo({ ...video, visibility: 'PRIVATE' }, 'chaine')).toBe(
      false,
    );
    expect(canStreamVideo({ ...video, visibility: 'UNLISTED' }, 'chaine')).toBe(
      false,
    );
  });

  it('refuse une vidéo Premium (ADR-007)', () => {
    expect(canStreamVideo({ ...video, isPremium: true }, 'chaine')).toBe(false);
  });
});

describe('ChatRateLimiter', () => {
  it('laisse passer un message par seconde et par compte', () => {
    let now = 10_000;
    const limiter = new ChatRateLimiter(1000, () => now);
    expect(limiter.tryAcquire('a')).toBe(true);
    now += 999;
    expect(limiter.tryAcquire('a')).toBe(false);
    now += 1;
    expect(limiter.tryAcquire('a')).toBe(true);
  });

  it('ne limite pas un compte à cause d’un autre', () => {
    const limiter = new ChatRateLimiter(1000, () => 10_000);
    expect(limiter.tryAcquire('a')).toBe(true);
    expect(limiter.tryAcquire('b')).toBe(true);
  });

  it('ne compte pas un message refusé', () => {
    let now = 10_000;
    const limiter = new ChatRateLimiter(1000, () => now);
    limiter.tryAcquire('a');
    now += 900;
    expect(limiter.tryAcquire('a')).toBe(false);
    now += 100; // 1 000 ms après le message accepté, pas après le refus
    expect(limiter.tryAcquire('a')).toBe(true);
  });
});

describe('toLiveView', () => {
  const record: LiveRecord = {
    id: 'live',
    title: 'Mon live',
    status: 'LIVE',
    startedAt: new Date('2026-11-05T10:00:00Z'),
    endedAt: null,
    channel: {
      id: 'chaine',
      name: 'Studio Libre',
      handle: 'studiolibre',
      avatarUrl: null,
      ownerId: 'proprietaire',
    },
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
  };

  it('ajoute le nombre de spectateurs', () => {
    expect(toLiveView(record, 7).viewerCount).toBe(7);
  });

  it("ne laisse sortir ni le propriétaire de la chaîne ni les règles d'accès de la vidéo", () => {
    const view = toLiveView(record, 0);
    expect(view.channel).not.toHaveProperty('ownerId');
    expect(view.video).not.toHaveProperty('visibility');
    expect(view.video).not.toHaveProperty('isPremium');
  });

  it('garde un live dont la vidéo a été supprimée', () => {
    expect(toLiveView({ ...record, video: null }, 0).video).toBeNull();
  });
});
