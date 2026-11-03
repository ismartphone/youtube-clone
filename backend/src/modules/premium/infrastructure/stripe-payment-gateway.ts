import Stripe from 'stripe';
import { PaymentUnavailableError } from '../domain/premium.errors.js';
import {
  PREMIUM_PRICE,
  type Payment,
  type PaymentStart,
} from '../domain/premium.js';
import type { PaymentGateway } from '../domain/ports.js';

// Les identifiants de PaymentIntent commencent par « pi_ » : toute autre référence est
// refusée sans appeler Stripe.
const PAYMENT_INTENT_PREFIX = 'pi_';

// Paiement par Stripe en mode test (ADR-014). La clé secrète reste ici, sur le serveur.
export class StripePaymentGateway implements PaymentGateway {
  readonly mode = 'stripe';
  private readonly stripe: Stripe;

  constructor(
    secretKey: string,
    readonly publishableKey: string,
  ) {
    this.stripe = new Stripe(secretKey);
  }

  // PaymentIntent au montant fixé par le serveur, rattaché à l'utilisateur par ses métadonnées.
  async start(userId: string): Promise<PaymentStart> {
    const intent = await this.call(() =>
      this.stripe.paymentIntents.create({
        amount: PREMIUM_PRICE.amount,
        currency: PREMIUM_PRICE.currency,
        automatic_payment_methods: { enabled: true },
        metadata: { userId },
      }),
    );
    if (!intent.client_secret) {
      throw new PaymentUnavailableError();
    }
    return {
      mode: this.mode,
      reference: intent.id,
      clientSecret: intent.client_secret,
      expiresAt: null,
    };
  }

  // Le serveur ne croit pas l'application sur parole : il relit le PaymentIntent chez Stripe.
  async verifyPayment(
    userId: string,
    reference: string,
  ): Promise<Payment | null> {
    if (!reference.startsWith(PAYMENT_INTENT_PREFIX)) {
      return null;
    }
    let intent: Stripe.PaymentIntent;
    try {
      intent = await this.stripe.paymentIntents.retrieve(reference);
    } catch (error) {
      // Identifiant inconnu de Stripe : refus. Toute autre erreur : Stripe indisponible.
      if (error instanceof Stripe.errors.StripeInvalidRequestError) {
        return null;
      }
      throw new PaymentUnavailableError();
    }
    const paid =
      intent.status === 'succeeded' &&
      intent.amount === PREMIUM_PRICE.amount &&
      intent.currency === PREMIUM_PRICE.currency &&
      intent.metadata.userId === userId;
    // Montant encaissé selon Stripe, et non le prix du code : c'est lui qui est enregistré.
    return paid
      ? { amount: intent.amount_received, currency: intent.currency }
      : null;
  }

  private async call<T>(request: () => Promise<T>): Promise<T> {
    try {
      return await request();
    } catch {
      throw new PaymentUnavailableError();
    }
  }
}
