import { normalizeEmail } from './user.js';

describe('normalizeEmail', () => {
  it('ignore la casse et les espaces autour', () => {
    expect(normalizeEmail('  Explorer@Example.COM ')).toBe(
      'explorer@example.com',
    );
  });
});
