import type { DeviceTokenRepository } from '../domain/ports.js';

// Jeton FCM de l'appareil (ADR-020) : enregistré à la connexion et à chaque renouvellement,
// retiré à la déconnexion.
export class ManageDeviceTokensUseCase {
  constructor(private readonly tokens: DeviceTokenRepository) {}

  register(userId: string, token: string): Promise<void> {
    return this.tokens.save(userId, token);
  }

  unregister(userId: string, token: string): Promise<void> {
    return this.tokens.remove(userId, token);
  }
}
