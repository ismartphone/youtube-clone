import { avatarColor, avatarInitials } from './avatar.js';

describe('avatarInitials', () => {
  it('prend les premières lettres des deux premiers mots', () => {
    expect(avatarInitials('Jazon Gaming')).toBe('JG');
    expect(avatarInitials('carnet de route')).toBe('CD');
  });

  it('découpe un nom en un seul mot écrit en CamelCase', () => {
    expect(avatarInitials('BeatEmUps')).toBe('BE');
    expect(avatarInitials('Hamxton')).toBe('H');
  });

  it('garde les lettres accentuées', () => {
    expect(avatarInitials('Élodie Ébène')).toBe('ÉÉ');
  });

  it('renvoie « ? » pour un nom vide', () => {
    expect(avatarInitials('   ')).toBe('?');
  });
});

describe('avatarColor', () => {
  it('donne une couleur hexadécimale sans « # », stable pour un même nom', () => {
    const color = avatarColor('Carnet de Route');
    expect(color).toMatch(/^[0-9a-f]{6}$/);
    expect(avatarColor('Carnet de Route')).toBe(color);
  });

  it('varie selon le nom', () => {
    expect(avatarColor('Studio Libre')).not.toBe(
      avatarColor('Carnet de Route'),
    );
  });
});
