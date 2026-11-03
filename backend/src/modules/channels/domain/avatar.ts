// Avatar généré d'une chaîne : les initiales de son nom sur un fond de couleur déduite du nom.
// C'est une valeur par défaut : une photo choisie par le créateur (avatar personnalisé, à venir)
// la remplacera dans avatarUrl, sans que le fichier généré ait à disparaître.

export const AVATAR_GENERATOR = Symbol('AVATAR_GENERATOR');

export interface AvatarGenerator {
  /**
   * Génère l'avatar de la chaîne nommée [name] sous /media/avatars/<fileKey>.png et renvoie son
   * URL relative, ou null en cas d'échec (journalisé) : un avatar manquant ne bloque rien.
   */
  generate(name: string, fileKey: string): Promise<string | null>;
}

// Initiales : premières lettres des deux premiers mots, ou des deux premières parties d'un nom
// en un seul mot écrit en CamelCase (« Jazon Gaming » : JG, « BeatEmUps » : BE, « Hamxton » : H).
export function avatarInitials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  const parts =
    words.length > 1
      ? words
      : (name.match(/\p{Lu}[\p{Ll}\d]*|[\p{Ll}\d]+/gu) ?? [name.trim()]);
  const initials = parts
    .slice(0, 2)
    .map((part) => [...part][0]?.toUpperCase() ?? '')
    .join('');
  return initials || '?';
}

// Couleur de fond déduite du nom, stable d'une génération à l'autre : teinte tirée d'un hachage
// du nom, saturation et luminosité fixes pour que les initiales blanches restent lisibles.
// Renvoie la couleur en hexadécimal, sans « # ».
export function avatarColor(name: string): string {
  let hash = 0;
  for (const char of name) {
    hash = (hash * 31 + char.codePointAt(0)!) >>> 0;
  }
  return hslToHex(hash % 360, 0.55, 0.42);
}

function hslToHex(hue: number, saturation: number, lightness: number): string {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const x = chroma * (1 - Math.abs(((hue / 60) % 2) - 1));
  const m = lightness - chroma / 2;
  const [r, g, b] =
    hue < 60
      ? [chroma, x, 0]
      : hue < 120
        ? [x, chroma, 0]
        : hue < 180
          ? [0, chroma, x]
          : hue < 240
            ? [0, x, chroma]
            : hue < 300
              ? [x, 0, chroma]
              : [chroma, 0, x];
  return [r, g, b]
    .map((c) =>
      Math.round((c + m) * 255)
        .toString(16)
        .padStart(2, '0'),
    )
    .join('');
}
