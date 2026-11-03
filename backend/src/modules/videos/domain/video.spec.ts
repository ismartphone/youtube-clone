import {
  ADMIN_VIEWER,
  ANONYMOUS_VIEWER,
  canSee,
  playableUrl,
  type Video,
} from './video.js';

function video(overrides: Partial<Video> = {}): Video {
  return {
    id: 'video-id',
    title: 'Titre',
    description: null,
    videoUrl: '/media/videos/a.mp4',
    hlsUrl: null,
    subtitleUrl: null,
    thumbnailUrl: '/media/thumbnails/a.jpg',
    durationSeconds: 60,
    format: 'LONG',
    isPremium: false,
    visibility: 'PUBLIC',
    commentsEnabled: true,
    viewCount: 0,
    likeCount: 0,
    publishedAt: new Date('2026-11-01T00:00:00Z'),
    channel: {
      id: 'channel-id',
      name: 'Chaîne',
      handle: 'chaine',
      avatarUrl: null,
      ownerId: 'owner-id',
    },
    category: null,
    ...overrides,
  };
}

describe('canSee', () => {
  it('cache une vidéo privée à tout autre que son propriétaire', () => {
    const privateVideo = video({ visibility: 'PRIVATE' });
    expect(canSee(privateVideo, ANONYMOUS_VIEWER)).toBe(false);
    expect(canSee(privateVideo, { isPremium: true, userId: 'other-id' })).toBe(
      false,
    );
    expect(canSee(privateVideo, { isPremium: false, userId: 'owner-id' })).toBe(
      true,
    );
  });

  it("montre tout à l'administrateur du back-office", () => {
    expect(canSee(video({ visibility: 'PRIVATE' }), ADMIN_VIEWER)).toBe(true);
  });
});

describe('playableUrl', () => {
  it("donne l'URL d'une vidéo gratuite à tout le monde", () => {
    expect(playableUrl(video(), ANONYMOUS_VIEWER)).toBe('/media/videos/a.mp4');
  });

  it("ne donne l'URL d'une vidéo exclusive qu'à un spectateur Premium", () => {
    const premiumVideo = video({ isPremium: true });
    expect(playableUrl(premiumVideo, ANONYMOUS_VIEWER)).toBeNull();
    expect(playableUrl(premiumVideo, { isPremium: true, userId: 'u' })).toBe(
      '/media/videos/a.mp4',
    );
  });
});
