import { likeDelta } from './like.repository.js';

describe('likeDelta', () => {
  it("compte un « j'aime » donné, et décompte un « j'aime » retiré", () => {
    expect(likeDelta(null, 'LIKE')).toBe(1);
    expect(likeDelta('LIKE', null)).toBe(-1);
  });

  it("décompte un « j'aime » remplacé par un avis négatif, et inversement", () => {
    expect(likeDelta('LIKE', 'DISLIKE')).toBe(-1);
    expect(likeDelta('DISLIKE', 'LIKE')).toBe(1);
  });

  it('ne compte jamais les avis négatifs', () => {
    expect(likeDelta(null, 'DISLIKE')).toBe(0);
    expect(likeDelta('DISLIKE', null)).toBe(0);
    expect(likeDelta('DISLIKE', 'DISLIKE')).toBe(0);
    expect(likeDelta('LIKE', 'LIKE')).toBe(0);
  });
});
