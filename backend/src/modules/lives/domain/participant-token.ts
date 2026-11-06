// Jeton de Participant du live partagé (ADR-031) : la preuve, signée par notre backend, de l'identité
// d'un utilisateur auprès du serveur commun. Aucun rôle : le serveur commun décide seul.

export const PARTICIPANT_TOKEN_SIGNER = Symbol('PARTICIPANT_TOKEN_SIGNER');

/** Durée de vie du jeton : le serveur commun refuse au-delà de 5 minutes. */
export const PARTICIPANT_TOKEN_TTL_SECONDS = 5 * 60;

/** Pseudo : 1 à 30 caractères visibles. */
export const PARTICIPANT_NAME_MAX = 30;

export interface ParticipantIdentity {
  // sub : identifiant de l'utilisateur chez nous.
  userId: string;
  name: string;
  // Absent sauf URL https : le serveur commun ignore une autre adresse.
  picture?: string;
}

export interface ParticipantTokenSigner {
  sign(identity: ParticipantIdentity): Promise<string>;
}

const graphemes = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

/**
 * Pseudo envoyé au serveur commun : le nom affiché, sans espaces autour, tronqué à 30 caractères
 * visibles sans jamais couper un emoji composé. null s'il ne reste rien.
 */
export function participantName(displayName: string): string | null {
  const name = Array.from(
    graphemes.segment(displayName.trim()),
    (s) => s.segment,
  )
    .slice(0, PARTICIPANT_NAME_MAX)
    .join('')
    .trim();
  return name.length > 0 ? name : null;
}

/** Avatar transmis seulement s'il est déjà une adresse https (ADR-031, D5). */
export function participantPicture(
  avatarUrl: string | null,
): string | undefined {
  return avatarUrl?.startsWith('https://') ? avatarUrl : undefined;
}
