import { formatProblem } from './video-upload.js';

const vertical = { width: 720, height: 1280 };
const horizontal = { width: 1280, height: 720 };

describe('formatProblem', () => {
  it('accepte toute vidéo classique, quelle que soit son orientation ou sa durée', () => {
    expect(
      formatProblem('LONG', { ...vertical, durationSeconds: 3600 }),
    ).toBeNull();
    expect(
      formatProblem('LONG', { ...horizontal, durationSeconds: 10 }),
    ).toBeNull();
  });

  it('accepte un Short vertical de 60 secondes, avec la marge d’arrondi', () => {
    expect(
      formatProblem('SHORT', { ...vertical, durationSeconds: 60 }),
    ).toBeNull();
    expect(
      formatProblem('SHORT', { ...vertical, durationSeconds: 60.02 }),
    ).toBeNull();
  });

  it('refuse un Short horizontal ou carré', () => {
    expect(formatProblem('SHORT', { ...horizontal, durationSeconds: 10 })).toBe(
      'Un Short doit être une vidéo verticale.',
    );
    expect(
      formatProblem('SHORT', { width: 720, height: 720, durationSeconds: 10 }),
    ).toBe('Un Short doit être une vidéo verticale.');
  });

  it('refuse un Short de plus de 60 secondes', () => {
    expect(formatProblem('SHORT', { ...vertical, durationSeconds: 61 })).toBe(
      'Un Short dure 60 secondes au plus.',
    );
  });
});
