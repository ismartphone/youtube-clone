import type { PremiumAccess } from '../domain/premium-access.js';
import { ANONYMOUS_VIEWER, type Viewer } from '../domain/video.js';

// Spectateur d'une requête : un visiteur n'est jamais Premium ; un utilisateur connecté l'est
// s'il a un abonnement en cours.
export class ResolveViewerUseCase {
  constructor(private readonly premium: PremiumAccess) {}

  async execute(userId: string | undefined): Promise<Viewer> {
    if (!userId) {
      return ANONYMOUS_VIEWER;
    }
    return { isPremium: await this.premium.isPremium(userId) };
  }
}
