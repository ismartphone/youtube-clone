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

export const CONVERSATION_ROOM_ACCESS = Symbol('CONVERSATION_ROOM_ACCESS');
export const LIVE_ROOM_ACCESS = Symbol('LIVE_ROOM_ACCESS');

// Salle d'une conversation (ADR-025) : réservée à ses deux participants.
export interface ConversationRoomAccess {
  // Faux pour un visiteur, un tiers, ou une conversation qui n'existe pas.
  isParticipant(conversationId: string, userId: string): Promise<boolean>;
}

// Salle d'un live (ADR-029) : ouverte à tous, visiteurs compris, tant que le live est en cours.
export interface LiveRoomAccess {
  // Faux pour un live terminé ou qui n'existe pas : le refus ne révèle rien.
  isRunning(liveId: string): Promise<boolean>;
}
