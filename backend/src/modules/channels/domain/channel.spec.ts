import { HANDLE_PATTERN, normalizeHandle } from './channel.js';

describe('normalizeHandle', () => {
  it('retire les espaces et le « @ », et passe en minuscules', () => {
    expect(normalizeHandle('  @Carnet.De-Route ')).toBe('carnet.de-route');
  });
});

describe('HANDLE_PATTERN', () => {
  it('accepte de 3 à 30 caractères parmi lettres minuscules, chiffres, « . », « _ » et « - »', () => {
    expect(HANDLE_PATTERN.test('abc')).toBe(true);
    expect(HANDLE_PATTERN.test('studio_libre-2026.fr')).toBe(true);
    expect(HANDLE_PATTERN.test('a'.repeat(30))).toBe(true);
  });

  it('refuse un handle trop court, trop long, en majuscules ou avec espace', () => {
    expect(HANDLE_PATTERN.test('ab')).toBe(false);
    expect(HANDLE_PATTERN.test('a'.repeat(31))).toBe(false);
    expect(HANDLE_PATTERN.test('Studio')).toBe(false);
    expect(HANDLE_PATTERN.test('mon studio')).toBe(false);
  });
});
