// Contrôle d'accès aux salles (ADR-010, ADR-016) : rejoindre la salle d'une vidéo qu'on n'a
// pas le droit de voir (privée, pour un autre que son propriétaire) est refusé, et les membres
// qui perdent ce droit en sont retirés.

export const VIDEO_ROOM_ACCESS = Symbol('VIDEO_ROOM_ACCESS');
export const VIDEO_ROOM_GUARD = Symbol('VIDEO_ROOM_GUARD');

export interface VideoRoomAccess {
  // Vrai si [userId] (null pour un visiteur) peut voir la vidéo ; faux si elle n'existe pas.
  canJoin(videoId: string, userId: string | null): Promise<boolean>;
}

export interface VideoRoomGuard {
  /**
   * Revérifie chaque membre de la salle après un changement de visibilité : ceux qui n'ont plus
   * accès à la vidéo en sont retirés, sinon la fuite continuerait tant qu'ils ne quittent pas l'écran.
   */
  revalidate(videoId: string): Promise<void>;
}
