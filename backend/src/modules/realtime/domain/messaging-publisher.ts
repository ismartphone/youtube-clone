// Port de diffusion de la messagerie (ADR-025) : un message part dans la salle de sa
// conversation (écran de la conversation) et dans celle de son destinataire (liste des
// conversations).

export const MESSAGING_PUBLISHER = Symbol('MESSAGING_PUBLISHER');

export interface MessagingEvents {
  'message:new': unknown;
}

export interface MessagingPublisher {
  toConversation<E extends keyof MessagingEvents>(
    conversationId: string,
    event: E,
    data: MessagingEvents[E],
  ): void;
  toUser<E extends keyof MessagingEvents>(
    userId: string,
    event: E,
    data: MessagingEvents[E],
  ): void;
}
