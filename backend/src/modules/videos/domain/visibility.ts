// Visibilité d'une vidéo (ADR-016) : qui voit l'entrée. Indépendante de l'accès Premium
// (ADR-007), qui dit qui peut la lire : les deux vérifications s'additionnent.

export const VIDEO_VISIBILITIES = ['PUBLIC', 'UNLISTED', 'PRIVATE'] as const;
export type VideoVisibility = (typeof VIDEO_VISIBILITIES)[number];

/**
 * Une vidéo privée n'est accessible qu'à son propriétaire ; publique ou non répertoriée, elle
 * l'est à tous par son lien direct. [ownerId] : propriétaire de la chaîne de la vidéo ;
 * [userId] : utilisateur de la requête, null pour un visiteur.
 */
export function canAccessVideo(
  visibility: VideoVisibility,
  ownerId: string,
  userId: string | null,
): boolean {
  return visibility !== 'PRIVATE' || userId === ownerId;
}
