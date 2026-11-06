import {
  participantName,
  participantPicture,
  type ParticipantTokenSigner,
} from '../domain/participant-token.js';
import type { LivesRepository } from '../domain/ports.js';

/** LIVE_SDK_APP_ID ou LIVE_SDK_SECRET absent de l'environnement. */
export class LiveSdkNotConfiguredError extends Error {
  constructor() {
    super("Le live partagé n'est pas configuré sur ce serveur");
  }
}

/** Compte introuvable, ou nom affiché vide : aucun pseudo valide à transmettre. */
export class ParticipantUnavailableError extends Error {
  constructor() {
    super('Aucun nom affiché utilisable pour ce compte');
  }
}

// Signe un Jeton de Participant frais pour l'utilisateur connecté (ADR-031). Appelé par le SDK à
// chaque connexion et reconnexion : rien n'est gardé en cache. [signer] null : non configuré.
export class IssueParticipantTokenUseCase {
  constructor(
    private readonly lives: LivesRepository,
    private readonly signer: ParticipantTokenSigner | null,
  ) {}

  async execute(userId: string): Promise<string> {
    if (!this.signer) throw new LiveSdkNotConfiguredError();
    const author = await this.lives.findAuthor(userId);
    const name = author && participantName(author.displayName);
    if (!author || !name) throw new ParticipantUnavailableError();
    return this.signer.sign({
      userId: author.id,
      name,
      picture: participantPicture(author.avatarUrl),
    });
  }
}
