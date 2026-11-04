// Port de diffusion en temps réel (ADR-004) : les cas d'usage publient un événement
// pour une vidéo, sans savoir comment il arrive aux clients.

export const VIDEO_ROOM_PUBLISHER = Symbol('VIDEO_ROOM_PUBLISHER');

export interface RealtimeEvents {
  'comment:new': unknown;
  // Réponse à un commentaire (ADR-023) : même forme, avec son parentId.
  'comment:reply': unknown;
  'views:update': { videoId: string; viewCount: number };
  'likes:update': { videoId: string; likeCount: number };
}

export interface VideoRoomPublisher {
  // Envoie l'événement à tous les clients présents dans la salle de la vidéo.
  publish<E extends keyof RealtimeEvents>(
    videoId: string,
    event: E,
    data: RealtimeEvents[E],
  ): void;
}
