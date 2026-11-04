// Port vu par le module messaging : prévenir le destinataire d'un message (ADR-025, étape 3).

export const MESSAGE_NOTIFIER = Symbol('MESSAGE_NOTIFIER');

export interface MessageNotifier {
  /**
   * Notifie le destinataire en arrière-plan, après la réponse de l'envoi. Ne lève jamais : un
   * échec est journalisé et le message reste envoyé. Sans effet s'il a coupé les notifications de
   * messages. [text] null : une vidéo partagée.
   */
  notify(
    recipientId: string,
    message: {
      conversationId: string;
      senderName: string;
      text: string | null;
    },
  ): void;
}
