import { canAccessVideo, isListedFor } from './visibility.js';

const OWNER = 'owner-id';
const OTHER = 'other-id';

describe('canAccessVideo', () => {
  it('ouvre une vidéo publique ou non répertoriée à tous, visiteurs compris', () => {
    for (const visibility of ['PUBLIC', 'UNLISTED'] as const) {
      expect(canAccessVideo(visibility, OWNER, null)).toBe(true);
      expect(canAccessVideo(visibility, OWNER, OTHER)).toBe(true);
    }
  });

  it('réserve une vidéo privée à son propriétaire', () => {
    expect(canAccessVideo('PRIVATE', OWNER, OWNER)).toBe(true);
    expect(canAccessVideo('PRIVATE', OWNER, OTHER)).toBe(false);
    expect(canAccessVideo('PRIVATE', OWNER, null)).toBe(false);
  });
});

describe('isListedFor', () => {
  it('ne liste que les vidéos publiques pour les autres', () => {
    expect(isListedFor('PUBLIC', OWNER, null)).toBe(true);
    expect(isListedFor('UNLISTED', OWNER, OTHER)).toBe(false);
    expect(isListedFor('PRIVATE', OWNER, null)).toBe(false);
  });

  it('liste toujours ses vidéos au propriétaire', () => {
    expect(isListedFor('UNLISTED', OWNER, OWNER)).toBe(true);
    expect(isListedFor('PRIVATE', OWNER, OWNER)).toBe(true);
  });
});
