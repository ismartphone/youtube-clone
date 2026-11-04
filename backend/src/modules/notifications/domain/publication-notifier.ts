// Port vu par le module videos : prévenir les abonnés qu'une vidéo vient d'être publiée (ADR-020).

export const PUBLICATION_NOTIFIER = Symbol('PUBLICATION_NOTIFIER');

export interface PublicationNotifier {
  /**
   * Annonce la vidéo en arrière-plan, après la réponse de la publication. Ne lève jamais : un
   * échec est journalisé et la vidéo reste publiée. Sans effet pour une vidéo privée, non
   * répertoriée ou déjà annoncée.
   */
  notify(videoId: string): void;
}
