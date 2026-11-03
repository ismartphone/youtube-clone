import { canReachComments } from './comment-rules.js';

describe('canReachComments', () => {
  it('refuse une vidéo inexistante', () => {
    expect(canReachComments(null, 'user-id')).toBe(false);
  });

  it("ouvre les commentaires d'une vidéo publique, même désactivés (la règle d'écriture est ailleurs)", () => {
    expect(
      canReachComments(
        { visibility: 'PUBLIC', ownerId: 'owner-id', commentsEnabled: false },
        null,
      ),
    ).toBe(true);
  });

  it("réserve les commentaires d'une vidéo privée à son propriétaire", () => {
    const target = {
      visibility: 'PRIVATE' as const,
      ownerId: 'owner-id',
      commentsEnabled: true,
    };
    expect(canReachComments(target, 'owner-id')).toBe(true);
    expect(canReachComments(target, 'other-id')).toBe(false);
  });
});
