import { avatarColor, avatarInitials, generatedAvatarKey } from './avatar.js';

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

describe('generatedAvatarKey', () => {
  const id = '4ac1e2f2-a8a4-4516-9e1e-7861578efd0f';

  it('redonne le même fichier pour le même nom', () => {
    expect(generatedAvatarKey(id, 'Carnet de Route')).toBe(
      generatedAvatarKey(id, 'Carnet de Route'),
    );
  });

  it('change de fichier quand le nom change, pour ne pas servir l’ancien gardé en cache', () => {
    expect(generatedAvatarKey(id, 'Carnet de Voyage')).not.toBe(
      generatedAvatarKey(id, 'Carnet de Route'),
    );
  });

  it("commence par l'identifiant de la chaîne, suivi de 8 chiffres hexadécimaux", () => {
    expect(generatedAvatarKey(id, 'Carnet de Route')).toMatch(
      new RegExp(`^${id}-[0-9a-f]{8}$`),
    );
  });
});
